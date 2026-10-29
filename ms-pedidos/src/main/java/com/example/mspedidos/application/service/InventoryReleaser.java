package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.InventoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.OrderRepositoryPort;
import com.example.mspedidos.domain.exception.InventoryAllocationConflictException;
import com.example.mspedidos.domain.exception.InventoryUnitNotFoundException;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.exception.OrderNotFoundException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;

/**
 * Paso de liberacion compartido por la cancelacion, el job de reintentos y deshacer una asignacion: relee la unidad
 * justo antes de llamar a ms-inventory (fuera de toda transaccion) y, si responde, limpia la marca en una transaccion
 * corta (orden -> unidad). Un 409/404 de ms-inventory significa que la unidad de pedido ya no tiene esa unidad de
 * inventario: se da por liberada (ERROR en el log para revision manual). Ante cualquier otro fallo registra WARN y deja
 * la marca para el siguiente reintento. No lanza por fallos de inventario ni de BD; solo lanza
 * {@link IllegalStateException} si se le llama dentro de una transaccion (error de programacion).
 */
@Slf4j
@Component
class InventoryReleaser {

    private final InventoryPort inventoryPort;
    private final OrderRepositoryPort orderRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final ItemHistoryRecorder history;
    private final TransactionTemplate tx;

    InventoryReleaser(InventoryPort inventoryPort, OrderRepositoryPort orderRepository,
                      OrderItemRepositoryPort itemRepository, ItemHistoryRecorder history,
                      PlatformTransactionManager transactionManager) {
        this.inventoryPort = inventoryPort;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.history = history;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Libera en ms-inventory la unidad pendiente de {@code item}; debe llamarse despues del commit que puso la marca.
     * {@code item} puede ser una foto vieja (p. ej. la lista del job): solo se llama a inventario si la unidad sigue
     * pendiente de liberar esa misma unidad de inventario.
     *
     * @return la unidad actualizada, o la ultima leida si no habia nada que liberar o algo fallo
     */
    OrderItem release(OrderItem item, String actor) {
        if (!item.isInventoryReleasePending() || item.getPendingReleaseUnitId() == null) {
            return item;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La liberacion en ms-inventory no puede ocurrir dentro de una transaccion");
        }
        Long unitId = item.getPendingReleaseUnitId();
        Long itemId = item.getId();
        OrderItem fresh;
        try {
            fresh = itemRepository.findByIdAndOrderId(itemId, item.getOrderId()).orElse(null);
        } catch (RuntimeException e) {
            log.warn("No se pudo releer la unidad de pedido {} antes de liberar la unidad de inventario {}: {}",
                    itemId, unitId, e.getMessage());
            return item;
        }
        if (fresh == null || !fresh.isInventoryReleasePending() || !unitId.equals(fresh.getPendingReleaseUnitId())) {
            // foto vieja: ya se libero o la marca apunta a otra unidad; liberar ahora podria soltar una asignacion viva
            return fresh == null ? item : fresh;
        }
        String outcome;
        try {
            inventoryPort.release(unitId, itemId);
            outcome = null;
        } catch (InventoryAllocationConflictException | InventoryUnitNotFoundException e) {
            outcome = "conflict:" + code(e);
            log.error("ms-inventory rechazo liberar la unidad {} de la unidad de pedido {} ({}); se da por liberada, "
                    + "revisar manualmente: {}", unitId, itemId, code(e), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("No se pudo liberar la unidad de inventario {} de la unidad de pedido {}; queda pendiente: {}",
                    unitId, itemId, e.getMessage());
            return fresh;
        }
        String to = outcome;
        try {
            return tx.execute(status -> {
                orderRepository.findByIdForUpdate(item.getOrderId())
                        .orElseThrow(() -> new OrderNotFoundException(item.getOrderId()));
                OrderItem locked = itemRepository.findByIdAndOrderIdForUpdate(itemId, item.getOrderId())
                        .orElseThrow(() -> new OrderItemNotFoundException(itemId));
                if (!locked.isInventoryReleasePending() || !unitId.equals(locked.getPendingReleaseUnitId())) {
                    // otro reintento ya la limpio (o la marca apunta a otra unidad): nada que hacer
                    return locked;
                }
                locked.markReleased();
                OrderItem saved = itemRepository.save(locked);
                history.record(itemId, HistoryEventType.INVENTORY_RELEASED, "unit:" + unitId, to, actor);
                return saved;
            });
        } catch (RuntimeException e) {
            log.warn("ms-inventory libero la unidad {} pero no se pudo limpiar la marca de la unidad de pedido {}; "
                    + "el job lo reintentara: {}", unitId, itemId, e.getMessage());
            return fresh;
        }
    }

    /** {@code InventoryUnitNotFoundException} -> {@code INVENTORY_UNIT_NOT_FOUND}. */
    private static String code(RuntimeException e) {
        return e.getClass().getSimpleName().replaceAll("Exception$", "")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }
}
