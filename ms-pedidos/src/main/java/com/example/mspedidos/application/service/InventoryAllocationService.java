package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.InventoryAllocationUseCase;
import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.InventoryPort;
import com.example.mspedidos.application.port.out.InventoryPort.AllocatedUnit;
import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.OrderRepositoryPort;
import com.example.mspedidos.domain.exception.InvalidStatusTransitionException;
import com.example.mspedidos.domain.exception.InventoryReleasePendingException;
import com.example.mspedidos.domain.exception.ItemSkuChangedException;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.exception.OrderNotFoundException;
import com.example.mspedidos.domain.exception.ProductLinkPendingException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Warehouse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asignacion desde inventario (spec 10.1) y reintento de liberaciones pendientes (10.2).
 * ms-inventory se llama siempre fuera de toda transaccion; la transaccion local toma orden -> unidad
 * (subconjunto del orden global orden -> cupo -> unidades). No toma el lock de cupo: cubrir desde Lima solo
 * libera cupo, nunca lo consume.
 */
@Slf4j
@Service
public class InventoryAllocationService implements InventoryAllocationUseCase {

    private final OrderRepositoryPort orderRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final ItemIncidentRepositoryPort incidentRepository;
    private final InventoryPort inventoryPort;
    private final ActorProvider actorProvider;
    private final ItemHistoryRecorder history;
    private final InventoryReleaser releaser;
    private final TransactionTemplate tx;

    InventoryAllocationService(OrderRepositoryPort orderRepository, OrderItemRepositoryPort itemRepository,
                               ItemIncidentRepositoryPort incidentRepository, InventoryPort inventoryPort,
                               ActorProvider actorProvider, ItemHistoryRecorder history, InventoryReleaser releaser,
                               PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.incidentRepository = incidentRepository;
        this.inventoryPort = inventoryPort;
        this.actorProvider = actorProvider;
        this.history = history;
        this.releaser = releaser;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public OrderItem allocate(Long orderId, Long itemId, Long inventoryUnitId) {
        OrderItem current = itemRepository.findByIdAndOrderId(itemId, orderId)
                .orElseThrow(() -> new OrderItemNotFoundException(itemId));
        if (inventoryUnitId.equals(current.getInventoryUnitId())) {
            return current; // reintento de una asignacion ya confirmada
        }
        // incluye unidad pendiente de vinculo (sin sku) -> 409 antes de llamar a ms-inventory
        ensureAllocatable(current);
        String actor = actorProvider.currentActor();

        String sku = current.getSku();
        AllocatedUnit allocated = inventoryPort.allocate(inventoryUnitId, itemId, sku);

        try {
            return tx.execute(status -> applyAllocation(orderId, itemId, inventoryUnitId, sku,
                    allocated.warehouse(), allocated.conditionCode(), actor));
        } catch (RuntimeException e) {
            compensate(orderId, itemId, inventoryUnitId);
            throw e;
        }
    }

    @Override
    public void retryPendingReleases() {
        String actor = actorProvider.currentActor();
        for (OrderItem item : itemRepository.findReleasePending()) {
            releaser.release(item, actor);
        }
    }

    private OrderItem applyAllocation(Long orderId, Long itemId, Long inventoryUnitId, String allocatedSku,
                                      Warehouse warehouse, String conditionCode, String actor) {
        orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        OrderItem item = itemRepository.findByIdAndOrderIdForUpdate(itemId, orderId)
                .orElseThrow(() -> new OrderItemNotFoundException(itemId));
        if (inventoryUnitId.equals(item.getInventoryUnitId())) {
            return item; // un reintento concurrente ya confirmo esta misma asignacion
        }
        // re-chequeo con lock: la unidad pudo cambiar mientras ms-inventory asignaba (sin sku -> 409 antes de comparar)
        ensureAllocatable(item);
        if (!allocatedSku.equals(item.getSku())) {
            throw new ItemSkuChangedException("El SKU de la unidad " + itemId + " cambio de " + allocatedSku
                    + " a " + item.getSku() + " mientras se asignaba desde inventario");
        }
        String fromStatus = item.getStatus().name();
        String fromImporter = ItemHistoryRecorder.importerLabel(item);
        item.coverFromInventory(inventoryUnitId, warehouse);
        OrderItem saved = itemRepository.save(item);
        // condicion de la unidad asignada (spec 13): queda en el historial junto a su almacen
        history.record(itemId, HistoryEventType.INVENTORY_ALLOCATED, null,
                "unit:" + inventoryUnitId + "@" + warehouse.name() + "/" + conditionCode, actor);
        history.record(itemId, HistoryEventType.STATUS_CHANGED, fromStatus, saved.getStatus().name(), actor);
        if (saved.getImporterType() == ImporterType.NOT_REQUIRED) {
            history.record(itemId, HistoryEventType.IMPORTER_ASSIGNED, fromImporter,
                    ImporterType.NOT_REQUIRED.name(), actor);
            incidentRepository.findOpen(itemId, IncidentType.IMPORTER_ASSIGNMENT_PENDING).ifPresent(incident -> {
                incident.resolve(actor);
                incidentRepository.save(incident);
                history.record(itemId, HistoryEventType.INCIDENT_RESOLVED,
                        IncidentType.IMPORTER_ASSIGNMENT_PENDING.name(), null, actor);
            });
        }
        return saved;
    }

    private static void ensureAllocatable(OrderItem item) {
        if (item.isInventoryReleasePending()) {
            throw new InventoryReleasePendingException("La unidad " + item.getId()
                    + " tiene pendiente liberar la unidad de inventario " + item.getPendingReleaseUnitId());
        }
        if (item.getStatus() != OrderItemStatus.PROCESSING || item.getInventoryUnitId() != null) {
            throw new InvalidStatusTransitionException("Solo una unidad en PROCESSING sin cobertura previa puede "
                    + "cubrirse desde inventario, actual: " + item.getStatus());
        }
        if (item.isProductLinkPending()) {
            throw new ProductLinkPendingException("La unidad " + item.getId() + " aun no tiene SKU interno (SKU de "
                    + "market " + item.getMarketSku() + " sin equivalencia en el catalogo); no puede cubrirse desde "
                    + "inventario");
        }
    }

    /**
     * La transaccion local fallo despues de que ms-inventory asigno: se libera (mejor esfuerzo) salvo que la
     * unidad de pedido ya referencie esa unidad de inventario (reintento concurrente confirmado) o no se pueda
     * comprobar; en la duda queda asignada de mas, que es el lado seguro (se repara reintentando la asignacion).
     * La comprobacion lee con lock (espera a que confirme una transaccion concurrente) y suelta el lock antes de
     * llamar a ms-inventory.
     */
    private void compensate(Long orderId, Long itemId, Long inventoryUnitId) {
        boolean referenced;
        try {
            referenced = Boolean.TRUE.equals(tx.execute(status -> {
                orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
                return itemRepository.findByIdAndOrderIdForUpdate(itemId, orderId)
                        .map(i -> inventoryUnitId.equals(i.getInventoryUnitId()))
                        .orElse(false);
            }));
        } catch (RuntimeException e) {
            log.warn("No se pudo comprobar la unidad de pedido {} tras fallar la asignacion; la unidad de inventario "
                    + "{} queda asignada hasta reintentar: {}", itemId, inventoryUnitId, e.getMessage());
            return;
        }
        if (referenced) {
            return;
        }
        try {
            inventoryPort.release(inventoryUnitId, itemId);
        } catch (RuntimeException e) {
            log.warn("No se pudo compensar la asignacion de la unidad de inventario {} a la unidad de pedido {}: {}",
                    inventoryUnitId, itemId, e.getMessage());
        }
    }
}
