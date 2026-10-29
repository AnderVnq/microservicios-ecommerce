package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.NotReceivedResult;
import com.example.mspedidos.application.port.in.PurchaseUseCase;
import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.application.port.in.RegisterPurchaseCommand;
import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.InventoryPort;
import com.example.mspedidos.application.port.out.InventoryPort.CreatePurchaseEntry;
import com.example.mspedidos.application.port.out.InventorySyncState;
import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.PurchaseFilter;
import com.example.mspedidos.application.port.out.PurchaseRepositoryPort;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.application.port.out.SupplierRepositoryPort;
import com.example.mspedidos.domain.exception.IdempotencyKeyReusedException;
import com.example.mspedidos.domain.exception.ItemSkuChangedException;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.exception.PurchaseNotFoundException;
import com.example.mspedidos.domain.exception.StatusChangeRequestPendingException;
import com.example.mspedidos.domain.exception.SupplierNotFoundException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Compras a proveedores (spec 8). Orden de locks: compra -> unidades de pedido (por id) -> solicitud; nunca se
 * bloquea una orden ni se llama a otro servicio con locks tomados. La sincronizacion con ms-inventory va por el
 * outbox {@code inventory_sync_tasks} y se intenta enseguida despues del commit; su fallo nunca falla la peticion.
 */
@Slf4j
@Service
public class PurchaseService implements PurchaseUseCase {

    static final int MAX_QUANTITY = 500;
    static final String DEFAULT_CURRENCY = "USD";
    static final int MAX_KEY_LENGTH = 100;
    /** Intentos de la transaccion de registro cuando una compra concurrente gana unidades elegidas. */
    static final int MAX_ATTEMPTS = 3;
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private final PurchaseRepositoryPort purchaseRepository;
    private final SupplierRepositoryPort supplierRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final StatusChangeRequestRepositoryPort requestRepository;
    private final InventorySyncTaskRepositoryPort taskRepository;
    private final InventoryPort inventoryPort;
    private final InventorySyncExecutor syncExecutor;
    private final UnitUrgencySelector selector;
    private final ItemHistoryRecorder history;
    private final ActorProvider actorProvider;
    private final ObjectMapper json;
    private final PurchaseArrivalService arrivals;
    private final PurchaseNonReceiptService nonReceipts;
    private final TransactionTemplate tx;

    PurchaseService(PurchaseRepositoryPort purchaseRepository, SupplierRepositoryPort supplierRepository,
                    OrderItemRepositoryPort itemRepository, StatusChangeRequestRepositoryPort requestRepository,
                    InventorySyncTaskRepositoryPort taskRepository, InventoryPort inventoryPort,
                    InventorySyncExecutor syncExecutor, UnitUrgencySelector selector, ItemHistoryRecorder history,
                    ActorProvider actorProvider, ObjectMapper json, PurchaseArrivalService arrivals,
                    PurchaseNonReceiptService nonReceipts, PlatformTransactionManager transactionManager) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.taskRepository = taskRepository;
        this.inventoryPort = inventoryPort;
        this.syncExecutor = syncExecutor;
        this.selector = selector;
        this.history = history;
        this.actorProvider = actorProvider;
        this.json = json;
        this.arrivals = arrivals;
        this.nonReceipts = nonReceipts;
        this.tx = new TransactionTemplate(transactionManager);
        // READ COMMITTED: el re-chequeo bajo lock de las unidades debe ver lo que confirmo una compra concurrente
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public PurchaseView register(String idempotencyKey, String requestHash, RegisterPurchaseCommand cmd) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "La cabecera Idempotency-Key es obligatoria (maximo " + MAX_KEY_LENGTH + " caracteres)");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("El hash de la peticion es obligatorio");
        }
        Optional<PurchaseView> replay = replayOf(idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        String currency = validate(cmd);
        // lectura remota antes de abrir la transaccion (caido -> InventoryUnavailableException, 503)
        if (!inventoryPort.isConditionActive(cmd.conditionCode())) {
            throw new IllegalArgumentException("La condicion no existe o esta inactiva: " + cmd.conditionCode());
        }
        String actor = actorProvider.currentActor();
        Long purchaseId = null;
        for (int attempt = 1; purchaseId == null; attempt++) {
            boolean lastAttempt = attempt >= MAX_ATTEMPTS;
            try {
                purchaseId = tx.execute(status -> create(idempotencyKey, requestHash, cmd, currency, actor,
                        lastAttempt));
            } catch (DataIntegrityViolationException e) {
                // otra peticion con la misma clave confirmo primero: la constraint unica decide
                return replayOf(idempotencyKey, requestHash).orElseThrow(() -> e);
            } catch (UnitUrgencySelector.RetrySelection e) {
                // una compra concurrente cubrio unidades elegidas: el rollback libero todos los locks; otra vuelta
                log.debug("Compra {}: unidades perdidas en el intento {}, se reintenta", idempotencyKey, attempt);
            } catch (PessimisticLockingFailureException e) {
                // red de seguridad (deadlock / timeout de lock): reintentar desde cero
                if (lastAttempt) {
                    throw e;
                }
                log.warn("Compra {}: conflicto de locks en el intento {}, se reintenta: {}", idempotencyKey,
                        attempt, e.getMessage());
            }
        }
        // ya confirmada y fuera de toda transaccion: un fallo deja la tarea PENDING para el job
        try {
            syncExecutor.runForPurchase(purchaseId);
        } catch (RuntimeException e) {
            log.warn("No se pudo sincronizar la compra {} con inventario: {}", purchaseId, e.getMessage());
        }
        return view(purchaseId, false);
    }

    @Override
    public PurchaseView registerArrival(Long purchaseId, String idempotencyKey, String requestHash, int quantity) {
        PurchaseArrivalService.Result result = arrivals.register(purchaseId, idempotencyKey, requestHash, quantity);
        return view(result.purchaseId(), result.replayed());
    }

    @Override
    public NotReceivedResult markNotReceived(Long purchaseId, String idempotencyKey, String requestHash, int quantity,
                                             String reason) {
        StatusChangeRequest request = nonReceipts.markNotReceived(purchaseId, idempotencyKey, requestHash, quantity,
                reason).request();
        return new NotReceivedResult(view(purchaseId, false), request);
    }

    @Override
    public PurchaseView retrySync(Long purchaseId) {
        syncExecutor.retryFailed(purchaseId);
        return view(purchaseId, false);
    }

    @Override
    public PurchaseView findOne(Long id) {
        return view(id, false);
    }

    @Override
    public List<Purchase> search(PurchaseFilter filter) {
        return purchaseRepository.search(filter);
    }

    private Long create(String key, String hash, RegisterPurchaseCommand cmd, String currency, String actor,
                        boolean lastAttempt) {
        Supplier supplier = supplierRepository.findById(cmd.supplierId())
                .orElseThrow(() -> new SupplierNotFoundException(cmd.supplierId()));
        supplier.ensureActive();
        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .idempotencyKey(key)
                .requestHash(hash)
                .supplierId(supplier.getId())
                .sku(cmd.sku())
                .conditionCode(cmd.conditionCode())
                .quantity(cmd.quantity())
                .unitCost(cmd.unitCost())
                .costCurrency(currency)
                .supplierPurchaseId(cmd.supplierPurchaseId())
                .invoiceNumber(blankToNull(cmd.invoiceNumber()))
                .expectedArrivalDate(cmd.expectedArrivalDate())
                .createdBy(actor)
                .createdAt(Instant.now())
                .build());
        Long purchaseId = purchase.getId();

        List<OrderItem> selected = hasExplicitItems(cmd)
                ? lockExplicit(cmd.orderItemIds(), cmd.sku())
                : selector.pickCoverable(cmd.sku(), cmd.quantity(), lastAttempt);
        for (OrderItem item : selected) {
            String from = item.getStatus().name();
            item.coverByPurchase(purchaseId);
            itemRepository.save(item);
            history.record(item.getId(), HistoryEventType.PURCHASE_COVERED, from, "purchase:" + purchaseId, actor);
            history.record(item.getId(), HistoryEventType.STATUS_CHANGED, from, OrderItemStatus.PURCHASED.name(),
                    actor);
        }

        List<Long> reservations = itemRepository.orderByUrgency(selected.stream().map(OrderItem::getId).toList());
        CreatePurchaseEntry entry = new CreatePurchaseEntry(purchaseId, purchase.getSupplierId(), purchase.getSku(),
                purchase.getConditionCode(), purchase.getQuantity(), purchase.getUnitCost(),
                purchase.getCostCurrency(), purchase.getExpectedArrivalDate(), reservations);
        taskRepository.save(InventorySyncTask.builder()
                .purchaseId(purchaseId)
                .type(InventorySyncTaskType.CREATE_ENTRY)
                .payload(json.writeValueAsString(entry))
                .idempotencyKey("purchase-" + purchaseId + "-entry")
                .status(InventorySyncTaskStatus.PENDING)
                .build());
        return purchaseId;
    }

    /** Exactamente esas unidades, bloqueadas por id; cada una debe poder cubrirse o falla toda la compra. */
    private List<OrderItem> lockExplicit(List<Long> ids, String sku) {
        Map<Long, OrderItem> locked = itemRepository.findByIdsForUpdate(ids).stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        for (Long id : ids) {
            OrderItem item = locked.get(id);
            if (item == null) {
                throw new OrderItemNotFoundException(id);
            }
            if (!sku.equals(item.getSku())) {
                throw new ItemSkuChangedException("La unidad " + id + " es del sku " + item.getSku()
                        + ", no del sku de la compra " + sku);
            }
            requestRepository.findPendingByOrderItemId(id).ifPresent(pending -> {
                throw new StatusChangeRequestPendingException("La unidad " + id
                        + " tiene una solicitud de cambio de estado pendiente: " + pending.getId());
            });
        }
        return ids.stream().map(locked::get).toList();
    }

    private Optional<PurchaseView> replayOf(String key, String hash) {
        return purchaseRepository.findByIdempotencyKey(key).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReusedException(
                        "La Idempotency-Key " + key + " ya se uso con otro cuerpo de peticion");
            }
            return view(existing.getId(), true);
        });
    }

    private PurchaseView view(Long id, boolean replayed) {
        Purchase purchase = purchaseRepository.findById(id).orElseThrow(() -> new PurchaseNotFoundException(id));
        List<OrderItem> covered = itemRepository.findByPurchaseId(id);
        long stillPurchased = covered.stream().filter(i -> i.getStatus() == OrderItemStatus.PURCHASED).count();
        int pendingSurplus = (int) Math.max(0, purchase.pending() - stillPurchased);
        return new PurchaseView(purchase, covered, pendingSurplus, purchaseRepository.findArrivals(id),
                purchaseRepository.findNonReceipts(id), syncState(taskRepository.findByPurchaseId(id)),
                warnings(purchase), replayed);
    }

    private List<String> warnings(Purchase purchase) {
        String invoice = purchase.getInvoiceNumber();
        if (invoice != null
                && purchaseRepository.existsOtherWithInvoice(purchase.getSupplierId(), invoice, purchase.getId())) {
            return List.of("Factura " + invoice + " ya registrada para este proveedor");
        }
        return List.of();
    }

    /** FAILED si alguna tarea fallo; si no, PENDING si alguna sigue pendiente; si no, OK. */
    static InventorySyncState syncState(List<InventorySyncTask> tasks) {
        if (tasks.stream().anyMatch(t -> t.getStatus() == InventorySyncTaskStatus.FAILED)) {
            return InventorySyncState.FAILED;
        }
        if (tasks.stream().anyMatch(t -> t.getStatus() == InventorySyncTaskStatus.PENDING)) {
            return InventorySyncState.PENDING;
        }
        return InventorySyncState.OK;
    }

    /** Valida el comando (422) y devuelve la moneda normalizada. */
    private static String validate(RegisterPurchaseCommand cmd) {
        require(cmd.supplierId() != null, "El proveedor es obligatorio");
        require(notBlank(cmd.sku()), "El sku es obligatorio");
        require(notBlank(cmd.conditionCode()), "La condicion es obligatoria");
        require(notBlank(cmd.supplierPurchaseId()), "El id de compra del proveedor es obligatorio");
        require(cmd.expectedArrivalDate() != null, "La fecha estimada de llegada es obligatoria");
        require(cmd.unitCost() != null && cmd.unitCost().signum() >= 0, "El costo unitario debe ser >= 0");
        require(cmd.quantity() >= 1 && cmd.quantity() <= MAX_QUANTITY,
                "La cantidad debe estar entre 1 y " + MAX_QUANTITY);
        String currency = cmd.costCurrency() == null ? DEFAULT_CURRENCY : cmd.costCurrency();
        require(CURRENCY.matcher(currency).matches(), "La moneda debe ser un codigo de 3 letras mayusculas");
        if (hasExplicitItems(cmd)) {
            List<Long> ids = cmd.orderItemIds();
            require(ids.stream().allMatch(java.util.Objects::nonNull), "orderItemIds no puede contener nulos");
            require(new HashSet<>(ids).size() == ids.size(), "orderItemIds no puede repetir unidades");
            require(ids.size() <= cmd.quantity(),
                    "No se pueden cubrir " + ids.size() + " unidades con una compra de " + cmd.quantity());
        }
        return currency;
    }

    private static boolean hasExplicitItems(RegisterPurchaseCommand cmd) {
        return cmd.orderItemIds() != null && !cmd.orderItemIds().isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
