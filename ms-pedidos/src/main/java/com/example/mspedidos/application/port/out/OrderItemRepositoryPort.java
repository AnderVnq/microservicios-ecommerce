package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.OrderItem;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepositoryPort {

    OrderItem save(OrderItem item);

    List<OrderItem> saveAll(List<OrderItem> items);

    Optional<OrderItem> findByIdAndOrderId(Long itemId, Long orderId);

    List<OrderItem> findByOrderId(Long orderId);

    /** Como {@link #findByIdAndOrderId} pero con lock pesimista de escritura; requiere transaccion. */
    Optional<OrderItem> findByIdAndOrderIdForUpdate(Long itemId, Long orderId);

    /** Unidades de la orden ordenadas por id, con lock pesimista de escritura; requiere transaccion. */
    List<OrderItem> findByOrderIdForUpdate(Long orderId);

    /**
     * Unidades que consumen cupo para el documento en el anio calendario de la orden.
     *
     * @param excludeItemId unidad a excluir del conteo; puede ser null
     */
    long countQuotaUnits(ImporterDocument document, int year, Long excludeItemId);
}
