package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.PurchaseEntryUseCase;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.application.port.out.InventoryEntryRepositoryPort;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort;
import com.example.msinventory.application.port.out.ItemConditionRepositoryPort;
import com.example.msinventory.application.port.out.PurchaseOperationRepositoryPort;
import com.example.msinventory.domain.exception.AllocationConflictException;
import com.example.msinventory.domain.exception.ConditionNotFoundException;
import com.example.msinventory.domain.exception.IdempotencyKeyReusedException;
import com.example.msinventory.domain.exception.NotEnoughPurchaseUnitsException;
import com.example.msinventory.domain.exception.PurchaseAlreadyRegisteredException;
import com.example.msinventory.domain.exception.PurchaseNotFoundException;
import com.example.msinventory.domain.model.EntryType;
import com.example.msinventory.domain.model.InventoryEntry;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.ItemCondition;
import com.example.msinventory.domain.model.MovementType;
import com.example.msinventory.domain.model.PurchaseOperation;
import com.example.msinventory.domain.model.PurchaseOperationType;
import com.example.msinventory.domain.model.Warehouse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;

@Service
public class PurchaseEntryService implements PurchaseEntryUseCase {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final int MAX_QUANTITY = 500;

    private final InventoryEntryRepositoryPort entries;
    private final InventoryUnitRepositoryPort units;
    private final ItemConditionRepositoryPort conditions;
    private final PurchaseOperationRepositoryPort operations;
    private final MovementRecorder recorder;
    private final ActorProvider actorProvider;
    private final TransactionTemplate tx;

    public PurchaseEntryService(InventoryEntryRepositoryPort entries, InventoryUnitRepositoryPort units,
                                ItemConditionRepositoryPort conditions, PurchaseOperationRepositoryPort operations,
                                MovementRecorder recorder, ActorProvider actorProvider,
                                PlatformTransactionManager transactionManager) {
        this.entries = entries;
        this.units = units;
        this.conditions = conditions;
        this.operations = operations;
        this.recorder = recorder;
        this.actorProvider = actorProvider;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- creacion

    @Override
    public PurchaseEntryResult create(String idempotencyKey, String requestHash, CreatePurchaseEntryCommand cmd) {
        requireKey(idempotencyKey);
        Optional<InventoryEntry> existing = entries.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, cmd);
        }
        validate(cmd);
        if (entries.findByPurchaseId(cmd.purchaseId()).isPresent()) {
            throw alreadyRegistered(cmd.purchaseId());
        }
        try {
            return tx.execute(status -> doCreate(idempotencyKey, requestHash, cmd));
        } catch (DataIntegrityViolationException e) {
            // Carrera: la transaccion ya hizo rollback; gana la clave repetida o la compra ya registrada.
            Optional<InventoryEntry> winner = entries.findByIdempotencyKey(idempotencyKey);
            if (winner.isPresent()) {
                return replay(winner.get(), requestHash, cmd);
            }
            if (entries.findByPurchaseId(cmd.purchaseId()).isPresent()) {
                throw alreadyRegistered(cmd.purchaseId());
            }
            // indice unico de allocated_order_item_id: otra unidad cubrio el item entre el pre-chequeo y el insert
            for (Long orderItemId : cmd.reservations()) {
                if (units.findByAllocatedOrderItemId(orderItemId).isPresent()) {
                    AllocationConflictException conflict = new AllocationConflictException(
                            "El item de pedido " + orderItemId + " ya esta cubierto por otra unidad");
                    conflict.initCause(e);
                    throw conflict;
                }
            }
            throw e;
        }
    }

    private PurchaseEntryResult replay(InventoryEntry entry, String requestHash, CreatePurchaseEntryCommand cmd) {
        if (entry.getRequestHash() == null || !entry.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException("La Idempotency-Key ya se uso con un cuerpo distinto");
        }
        // Mismo hash => cmd.reservations() es la lista original; se empareja por posicion, no por estado vivo.
        return new PurchaseEntryResult(entry, units.findByEntryId(entry.getId()), cmd.reservations(), true);
    }

    private void validate(CreatePurchaseEntryCommand cmd) {
        if (cmd.purchaseId() == null || cmd.supplierId() == null || cmd.unitCost() == null
                || cmd.expectedArrivalDate() == null || cmd.sku() == null || cmd.sku().isBlank()
                || cmd.conditionCode() == null || cmd.conditionCode().isBlank()) {
            throw new IllegalArgumentException(
                    "purchaseId, supplierId, sku, conditionCode, unitCost y expectedArrivalDate son obligatorios");
        }
        if (cmd.quantity() < 1 || cmd.quantity() > MAX_QUANTITY) {
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + MAX_QUANTITY);
        }
        List<Long> reservations = cmd.reservations();
        if (reservations.size() > cmd.quantity()) {
            throw new IllegalArgumentException("Hay mas reservas (" + reservations.size()
                    + ") que unidades (" + cmd.quantity() + ")");
        }
        if (reservations.stream().anyMatch(r -> r == null) || new HashSet<>(reservations).size() != reservations.size()) {
            throw new IllegalArgumentException("Las reservas deben ser ids de item de pedido distintos");
        }
    }

    private PurchaseEntryResult doCreate(String idempotencyKey, String requestHash, CreatePurchaseEntryCommand cmd) {
        String conditionCode = cmd.conditionCode().trim().toUpperCase(Locale.ROOT);
        // Una compra a proveedor no exige condicion activa (spec 7), pero la condicion debe existir.
        ItemCondition condition = conditions.findByCode(conditionCode)
                .orElseThrow(() -> new ConditionNotFoundException("Condicion no encontrada: " + conditionCode));
        for (Long orderItemId : cmd.reservations()) {
            if (units.findByAllocatedOrderItemId(orderItemId).isPresent()) {
                throw new AllocationConflictException("El item de pedido " + orderItemId + " ya esta cubierto por otra unidad");
            }
        }

        String actor = actorProvider.currentActor();
        InventoryEntry entry = entries.save(InventoryEntry.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .entryType(EntryType.SUPPLIER_PURCHASE)
                .quantity(cmd.quantity())
                .performedBy(actor)
                .purchaseId(cmd.purchaseId())
                .supplierId(cmd.supplierId())
                .createdAt(Instant.now())
                .build());

        String currency = hasText(cmd.costCurrency()) ? cmd.costCurrency() : DEFAULT_CURRENCY;
        List<InventoryUnit> newUnits = new ArrayList<>(IntStream.range(0, cmd.quantity())
                .mapToObj(i -> InventoryUnit.newUnit(cmd.sku(), condition.getCode(), Warehouse.USA,
                        InventoryUnitStatus.IN_TRANSIT, cmd.unitCost(), currency, EntryType.SUPPLIER_PURCHASE,
                        null, null, null, cmd.expectedArrivalDate(), null, entry.getId(),
                        cmd.supplierId(), cmd.purchaseId()))
                .toList());
        for (int i = 0; i < cmd.reservations().size(); i++) {
            newUnits.get(i).reserve(cmd.reservations().get(i));
        }
        List<InventoryUnit> saved = units.saveAll(newUnits);
        saved.forEach(u -> recorder.record(u, MovementType.ENTRY_SUPPLIER_PURCHASE, null, Warehouse.USA,
                purchaseReference(cmd.purchaseId()), null, actor));
        return new PurchaseEntryResult(entry, saved, cmd.reservations(), false);
    }

    // ---------------------------------------------------------------- llegadas / no recibidos

    @Override
    public List<Long> registerArrivals(String idempotencyKey, String requestHash, Long purchaseId, int quantity,
                                       List<Long> orderItemIds) {
        return operate(idempotencyKey, requestHash, purchaseId, quantity, orderItemIds,
                PurchaseOperationType.ARRIVAL, null);
    }

    @Override
    public List<Long> markNotReceived(String idempotencyKey, String requestHash, Long purchaseId, int quantity,
                                      List<Long> orderItemIds, String reason) {
        if (!hasText(reason)) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        return operate(idempotencyKey, requestHash, purchaseId, quantity, orderItemIds,
                PurchaseOperationType.NOT_RECEIVED, reason);
    }

    private List<Long> operate(String idempotencyKey, String requestHash, Long purchaseId, int quantity,
                               List<Long> orderItemIds, PurchaseOperationType type, String reason) {
        requireKey(idempotencyKey);
        if (purchaseId == null) {
            throw new IllegalArgumentException("El purchaseId es obligatorio");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + MAX_QUANTITY);
        }
        Set<Long> itemIds = new HashSet<>(orderItemIds == null ? List.of() : orderItemIds);
        try {
            return tx.execute(status -> doOperate(idempotencyKey, requestHash, purchaseId, quantity, itemIds,
                    type, reason));
        } catch (DataIntegrityViolationException e) {
            PurchaseOperation winner = operations.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return replay(winner, requestHash, purchaseId, type);
        }
    }

    private List<Long> doOperate(String idempotencyKey, String requestHash, Long purchaseId, int quantity,
                                 Set<Long> orderItemIds, PurchaseOperationType type, String reason) {
        // Se bloquean las unidades de la compra primero: una peticion concurrente con la misma clave espera
        // y despues encuentra la operacion ya guardada (replay) en vez de fallar por falta de unidades.
        List<InventoryUnit> purchaseUnits = units.findByPurchaseIdForUpdate(purchaseId);
        if (purchaseUnits.isEmpty()) {
            throw new PurchaseNotFoundException("Compra no encontrada: " + purchaseId);
        }
        Optional<PurchaseOperation> existing = operations.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash, purchaseId, type);
        }
        List<InventoryUnit> selected = select(purchaseUnits, orderItemIds, quantity);
        String actor = actorProvider.currentActor();
        Consumer<InventoryUnit> apply = type == PurchaseOperationType.ARRIVAL
                ? u -> arrive(u, purchaseId, actor)
                : u -> notReceived(u, purchaseId, reason, actor);
        selected.forEach(apply);
        List<Long> unitIds = selected.stream().map(InventoryUnit::getId).toList();
        operations.save(new PurchaseOperation(null, idempotencyKey, requestHash, purchaseId, type, unitIds,
                Instant.now()));
        return unitIds;
    }

    /** Primero las reservadas a los items indicados (por id), luego las libres (por id), solo en transito. */
    private List<InventoryUnit> select(List<InventoryUnit> purchaseUnits, Set<Long> orderItemIds, int quantity) {
        List<InventoryUnit> picked = new ArrayList<>();
        purchaseUnits.stream()
                .filter(u -> u.getStatus() == InventoryUnitStatus.IN_TRANSIT
                        && u.getAllocatedOrderItemId() != null && orderItemIds.contains(u.getAllocatedOrderItemId()))
                .limit(quantity).forEach(picked::add);
        if (picked.size() < quantity) {
            purchaseUnits.stream()
                    .filter(u -> u.getStatus() == InventoryUnitStatus.IN_TRANSIT && u.getAllocatedOrderItemId() == null)
                    .limit(quantity - picked.size()).forEach(picked::add);
        }
        if (picked.size() < quantity) {
            throw new NotEnoughPurchaseUnitsException("La compra no tiene " + quantity
                    + " unidades en transito disponibles (hay " + picked.size() + ")");
        }
        return picked;
    }

    private void arrive(InventoryUnit unit, Long purchaseId, String actor) {
        if (!unit.arriveFromPurchase()) {
            return;
        }
        units.save(unit);
        recorder.record(unit, MovementType.ARRIVAL, null, unit.getWarehouse(), purchaseReference(purchaseId), null, actor);
        if (unit.getStatus() == InventoryUnitStatus.ALLOCATED) {
            recorder.record(unit, MovementType.EXIT_ORDER_ALLOCATION, unit.getWarehouse(), null,
                    "order-item:" + unit.getAllocatedOrderItemId(), null, actor);
        }
    }

    private void notReceived(InventoryUnit unit, Long purchaseId, String reason, String actor) {
        if (!unit.markNotReceived()) {
            return;
        }
        units.save(unit);
        recorder.record(unit, MovementType.EXIT_NOT_RECEIVED, unit.getWarehouse(), null,
                purchaseReference(purchaseId), reason, actor);
    }

    private List<Long> replay(PurchaseOperation op, String requestHash, Long purchaseId, PurchaseOperationType type) {
        if (op.requestHash() == null || !op.requestHash().equals(requestHash)
                || !op.purchaseId().equals(purchaseId) || op.type() != type) {
            throw new IdempotencyKeyReusedException("La Idempotency-Key ya se uso con una solicitud distinta");
        }
        return op.unitIds();
    }

    // ---------------------------------------------------------------- utilidades

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La cabecera Idempotency-Key es obligatoria");
        }
    }

    private static PurchaseAlreadyRegisteredException alreadyRegistered(Long purchaseId) {
        return new PurchaseAlreadyRegisteredException("La compra " + purchaseId + " ya esta registrada en inventario");
    }

    private static String purchaseReference(Long purchaseId) {
        return "purchase:" + purchaseId;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
