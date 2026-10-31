package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.EntryResult;
import com.example.msinventory.application.port.in.InventoryEntryUseCase;
import com.example.msinventory.application.port.in.RegisterEntryCommand;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.application.port.out.InventoryEntryRepositoryPort;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort;
import com.example.msinventory.application.port.out.ItemConditionRepositoryPort;
import com.example.msinventory.application.port.out.ProductsPort;
import com.example.msinventory.domain.exception.UnknownSkuException;
import com.example.msinventory.domain.exception.ConditionInactiveException;
import com.example.msinventory.domain.exception.ConditionNotFoundException;
import com.example.msinventory.domain.exception.IdempotencyKeyReusedException;
import com.example.msinventory.domain.model.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
public class InventoryEntryService implements InventoryEntryUseCase {

    private static final String DEFAULT_CURRENCY = "USD";

    private final InventoryEntryRepositoryPort entries;
    private final InventoryUnitRepositoryPort units;
    private final ItemConditionRepositoryPort conditions;
    private final MovementRecorder recorder;
    private final ActorProvider actorProvider;
    private final ProductsPort products;
    private final TransactionTemplate tx;

    public InventoryEntryService(InventoryEntryRepositoryPort entries, InventoryUnitRepositoryPort units,
                                 ItemConditionRepositoryPort conditions, MovementRecorder recorder,
                                 ActorProvider actorProvider, ProductsPort products,
                                 PlatformTransactionManager transactionManager) {
        this.entries = entries;
        this.units = units;
        this.conditions = conditions;
        this.recorder = recorder;
        this.actorProvider = actorProvider;
        this.products = products;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public EntryResult register(String idempotencyKey, String requestHash, RegisterEntryCommand request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La cabecera Idempotency-Key es obligatoria");
        }
        Optional<InventoryEntry> existing = entries.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }
        validate(request);
        // Llamada remota antes de abrir la transaccion. Se guarda la escritura del catalogo: la asignacion compara
        // el SKU de la unidad de pedido (escritura del catalogo) distinguiendo mayusculas.
        String canonicalSku = products.canonicalSku(request.sku().trim())
                .orElseThrow(() -> new UnknownSkuException("El SKU no existe en el catalogo"));
        RegisterEntryCommand cmd = request.withSku(canonicalSku);
        try {
            return tx.execute(status -> create(idempotencyKey, requestHash, cmd));
        } catch (DataIntegrityViolationException e) {
            // Carrera: otra peticion con la misma clave nueva gano; la transaccion ya hizo rollback.
            InventoryEntry winner = entries.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return replay(winner, requestHash);
        }
    }

    private EntryResult replay(InventoryEntry entry, String requestHash) {
        if (entry.getRequestHash() == null || !entry.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException("La Idempotency-Key ya se uso con un cuerpo distinto");
        }
        return new EntryResult(entry, units.findByEntryId(entry.getId()), List.of(), true);
    }

    private void validate(RegisterEntryCommand cmd) {
        if (cmd.entryType() == null || cmd.warehouse() == null || cmd.unitCost() == null
                || cmd.sku() == null || cmd.sku().isBlank()
                || cmd.conditionCode() == null || cmd.conditionCode().isBlank()) {
            throw new IllegalArgumentException("entryType, sku, conditionCode, warehouse y unitCost son obligatorios");
        }
        if (cmd.quantity() < 1) {
            throw new IllegalArgumentException("La cantidad debe ser al menos 1");
        }
        if (cmd.status() != InventoryUnitStatus.IN_TRANSIT && cmd.status() != InventoryUnitStatus.AVAILABLE) {
            throw new IllegalArgumentException("El estado inicial solo puede ser IN_TRANSIT o AVAILABLE");
        }
        if (cmd.status() == InventoryUnitStatus.IN_TRANSIT && cmd.expectedArrivalDate() == null) {
            throw new IllegalArgumentException("La fecha estimada de llegada es obligatoria para unidades en transito");
        }
        if (cmd.entryType() == EntryType.ADJUSTMENT_IN && (cmd.reason() == null || cmd.reason().isBlank())) {
            throw new IllegalArgumentException("El motivo es obligatorio para un ajuste de entrada");
        }
    }

    private EntryResult create(String idempotencyKey, String requestHash, RegisterEntryCommand cmd) {
        String conditionCode = cmd.conditionCode().trim().toUpperCase(Locale.ROOT);
        ItemCondition condition = conditions.findByCode(conditionCode)
                .orElseThrow(() -> new ConditionNotFoundException("Condicion no encontrada: " + conditionCode));
        if (!condition.isActive()) {
            throw new ConditionInactiveException("La condicion " + conditionCode + " esta inactiva");
        }

        List<String> warnings = new ArrayList<>();
        if (hasText(cmd.invoiceNumber()) && entries.existsOtherWithInvoice(cmd.invoiceNumber())) {
            warnings.add("Factura " + cmd.invoiceNumber() + " ya registrada");
        }

        String actor = actorProvider.currentActor();
        InventoryEntry entry = entries.save(InventoryEntry.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .entryType(cmd.entryType())
                .quantity(cmd.quantity())
                .performedBy(actor)
                .createdAt(Instant.now())
                .build());

        String currency = hasText(cmd.costCurrency()) ? cmd.costCurrency() : DEFAULT_CURRENCY;
        List<InventoryUnit> newUnits = IntStream.range(0, cmd.quantity())
                .mapToObj(i -> InventoryUnit.newUnit(cmd.sku(), conditionCode, cmd.warehouse(), cmd.status(),
                        cmd.unitCost(), currency, cmd.entryType(), cmd.invoiceNumber(), cmd.supplierPurchaseId(),
                        cmd.sourceReference(), cmd.expectedArrivalDate(), cmd.supplierReturnDeadline(),
                        entry.getId()))
                .toList();
        List<InventoryUnit> saved = units.saveAll(newUnits);

        String reference = hasText(cmd.invoiceNumber()) ? cmd.invoiceNumber() : cmd.sourceReference();
        MovementType type = MovementType.entryFor(cmd.entryType());
        saved.forEach(u -> recorder.record(u, type, null, u.getWarehouse(), reference, cmd.reason(), actor));
        return new EntryResult(entry, saved, warnings, false);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
