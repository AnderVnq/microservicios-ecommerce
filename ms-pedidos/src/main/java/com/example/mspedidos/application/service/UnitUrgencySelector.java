package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elige y bloquea las unidades de pedido que cubre una compra sin {@code orderItemIds} (spec 8.1): las
 * {@code PROCESSING} del sku sin cobertura, de la mas urgente a la menos. Debe llamarse dentro de la transaccion
 * de la compra.
 * <p>
 * Un intento = una sola seleccion sin lock + un solo {@code findByIdsForUpdate} (bloqueo por id ascendente, nunca
 * en orden de urgencia); bajo el lock se re-chequea cada unidad, porque una compra concurrente pudo cubrirla entre
 * la lectura y el lock. Si se perdio alguna y no es el ultimo intento se lanza {@link RetrySelection}: el llamador
 * deshace la transaccion (libera todos los locks) y vuelve a empezar, asi nunca se bloquea un id menor despues de
 * uno mayor dentro de la misma transaccion. En el ultimo intento se usa lo que se tiene y el resto es sobrante.
 */
@Component
@RequiredArgsConstructor
class UnitUrgencySelector {

    private final OrderItemRepositoryPort itemRepository;
    private final StatusChangeRequestRepositoryPort requestRepository;

    /** Senal interna: se perdieron unidades en este intento; reintentar la transaccion completa. */
    static final class RetrySelection extends RuntimeException {
        RetrySelection() {
            super("Unidades cubiertas por una compra concurrente; se reintenta la seleccion", null, false, false);
        }
    }

    /**
     * Como mucho {@code quantity} unidades bloqueadas y cubribles, de la mas urgente a la menos.
     *
     * @throws RetrySelection si se perdio alguna unidad y {@code lastAttempt} es false
     */
    List<OrderItem> pickCoverable(String sku, int quantity, boolean lastAttempt) {
        List<Long> candidates = itemRepository.findCoverableIdsByUrgency(sku, quantity);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, OrderItem> held = new HashMap<>();
        for (OrderItem item : itemRepository.findByIdsForUpdate(candidates)) {
            if (isCoverable(item, sku)) {
                held.put(item.getId(), item);
            }
        }
        if (held.size() < candidates.size() && !lastAttempt) {
            throw new RetrySelection();
        }
        return itemRepository.orderByUrgency(held.keySet()).stream().map(held::get).toList();
    }

    private boolean isCoverable(OrderItem item, String sku) {
        return item.getStatus() == OrderItemStatus.PROCESSING
                && sku.equals(item.getSku())
                && item.getPurchaseId() == null
                && item.getInventoryUnitId() == null
                && !item.isInventoryReleasePending()
                && requestRepository.findPendingByOrderItemId(item.getId()).isEmpty();
    }
}
