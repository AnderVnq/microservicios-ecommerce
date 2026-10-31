package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.OrderItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderItemRepositoryPort {

    OrderItem save(OrderItem item);

    List<OrderItem> saveAll(List<OrderItem> items);

    Optional<OrderItem> findById(Long itemId);

    Optional<OrderItem> findByIdAndOrderId(Long itemId, Long orderId);

    List<OrderItem> findByOrderId(Long orderId);

    /** Unidades de varias ordenes en una sola consulta, por id ascendente. */
    List<OrderItem> findByOrderIds(Collection<Long> orderIds);

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

    /** Unidades con {@code inventory_release_pending = true}, por id ascendente. */
    List<OrderItem> findReleasePending();

    /**
     * Unidades filtradas por {@code inventory_release_pending} y por pendiente de vinculo ({@code sku} null);
     * {@code null} no filtra. Por id ascendente.
     */
    List<OrderItem> search(Boolean inventoryReleasePending, Boolean productLinkPending);

    /** Pares (market, SKU de market) distintos de las unidades no terminales sin SKU interno. Sin lock. */
    List<MarketSkuKey> findPendingMarketSkus();

    /**
     * Ids de las unidades no terminales sin SKU interno de ese market con ese SKU de market (comparado tal como se
     * guarda), por id ascendente. Sin lock.
     */
    List<Long> findPendingIds(Long marketId, String marketSku);

    /**
     * Ids de unidades {@code PROCESSING} del sku sin compra ni unidad fisica, sin marca de liberacion y sin
     * solicitud {@code PENDING}, de la mas urgente a la menos (fecha limite asc nulls last, fecha de orden, id).
     * Sin lock.
     */
    List<Long> findCoverableIdsByUrgency(String sku, int limit);

    /** Unidades por id ascendente con lock pesimista de escritura; requiere transaccion. */
    List<OrderItem> findByIdsForUpdate(Collection<Long> ids);

    /** Unidades cubiertas por la compra, por id ascendente. Sin lock. */
    List<OrderItem> findByPurchaseId(Long purchaseId);

    /** Unidades cubiertas por la compra, por id ascendente, con lock pesimista; requiere transaccion. */
    List<OrderItem> findByPurchaseIdForUpdate(Long purchaseId);

    /** Los mismos ids ordenados de la mas urgente a la menos. */
    List<Long> orderByUrgency(Collection<Long> itemIds);
}
