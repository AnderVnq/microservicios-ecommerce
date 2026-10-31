package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.DeliveredTo;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Warehouse;

import java.math.BigDecimal;
import java.util.List;

public interface OrderItemUseCase {

    /** Agrega {@code line.quantity()} unidades a una orden existente con la misma logica de cupo que al crear. */
    List<OrderItem> addUnits(Long orderId, OrderLine line);

    OrderItem assignImporter(Long orderId, Long itemId, AssignImporterCommand command);

    List<OrderItem> findByOrder(Long orderId);

    OrderItem findOne(Long orderId, Long itemId);

    /**
     * Edita una unidad {@code PROCESSING}. {@code sku} y {@code marketSku} null conservan el valor actual; un
     * {@code sku} se valida contra el catalogo y, si la unidad estaba pendiente de vinculo, cierra su incidencia.
     */
    OrderItem updateDetails(Long orderId, Long itemId, String sku, String marketSku, String externalItemId,
                            BigDecimal unitPrice);

    /** {@code CANCELLED} cancela la unidad; cualquier otro estado sigue la maquina de estados. */
    OrderItem changeStatus(Long orderId, Long itemId, OrderItemStatus status, Warehouse warehouse,
                           DeliveredTo deliveredTo);

    /** Unidades filtradas por {@code inventoryReleasePending} y {@code productLinkPending}; {@code null} no filtra. */
    List<OrderItem> search(Boolean inventoryReleasePending, Boolean productLinkPending);

    /** Historial cronologico de la unidad. */
    List<OrderItemHistoryEntry> history(Long orderId, Long itemId);
}
