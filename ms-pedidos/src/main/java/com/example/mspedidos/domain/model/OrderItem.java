package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.ImporterChangeNotAllowedException;
import com.example.mspedidos.domain.exception.ImporterRequiredException;
import com.example.mspedidos.domain.exception.InvalidStatusTransitionException;
import com.example.mspedidos.domain.exception.ItemNotEditableException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/** Una unidad fisica de una orden. */
@Getter
@Builder
@AllArgsConstructor
public class OrderItem {

    private Long id;
    private Long orderId;
    private String sku;
    private String externalItemId;
    private BigDecimal unitPrice;
    private OrderItemStatus status;
    private Warehouse warehouse;
    private DeliveredTo deliveredTo;
    private ImporterType importerType;
    private Long familyMemberId;
    private ImporterDocument importerDocument;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderItem newUnit(String sku, String externalItemId, BigDecimal unitPrice) {
        return OrderItem.builder()
                .sku(sku)
                .externalItemId(externalItemId)
                .unitPrice(unitPrice)
                .status(OrderItemStatus.PROCESSING)
                .importerType(ImporterType.UNASSIGNED)
                .build();
    }

    /** Vincula una unidad nueva a su orden; una unidad no cambia de orden. */
    public void attachTo(Long orderId) {
        if (this.orderId != null && !this.orderId.equals(orderId)) {
            throw new IllegalStateException("La unidad ya pertenece a la orden " + this.orderId);
        }
        this.orderId = orderId;
    }

    public void assignHolder(ImporterDocument doc) {
        ensureImporterChangeAllowed();
        this.importerType = ImporterType.HOLDER;
        this.familyMemberId = null;
        this.importerDocument = doc;
    }

    public void assignFamilyMember(Long familyMemberId, ImporterDocument doc) {
        ensureImporterChangeAllowed();
        this.importerType = ImporterType.FAMILY_MEMBER;
        this.familyMemberId = familyMemberId;
        this.importerDocument = doc;
    }

    public void changeStatus(OrderItemStatus next, Warehouse warehouse, DeliveredTo deliveredTo) {
        if (next == OrderItemStatus.CANCELLED) {
            throw new InvalidStatusTransitionException("Para cancelar una unidad use la operacion de cancelacion");
        }
        if (!status.canMoveTo(next)) {
            throw new InvalidStatusTransitionException(
                    "Transicion de estado no permitida: " + status + " -> " + next);
        }
        if (next.ordinal() >= OrderItemStatus.READY_TO_SHIP.ordinal()
                && importerType == ImporterType.UNASSIGNED) {
            throw new ImporterRequiredException(
                    "La unidad no tiene importador asignado; no puede pasar a " + next);
        }
        if (next == OrderItemStatus.RECEIVED_AT_WAREHOUSE && warehouse == null) {
            throw new InvalidStatusTransitionException("El almacen es obligatorio para " + next);
        }
        if (next == OrderItemStatus.DELIVERED && deliveredTo == null) {
            throw new InvalidStatusTransitionException("El destino de entrega es obligatorio para " + next);
        }
        if (next == OrderItemStatus.RECEIVED_AT_WAREHOUSE) {
            this.warehouse = warehouse;
        }
        if (next == OrderItemStatus.DELIVERED) {
            this.deliveredTo = deliveredTo;
        }
        this.status = next;
    }

    public void cancel() {
        if (status.isTerminal()) {
            throw new InvalidStatusTransitionException("No se puede cancelar una unidad en estado " + status);
        }
        this.status = OrderItemStatus.CANCELLED;
    }

    public void updateDetails(String sku, String externalItemId, BigDecimal unitPrice) {
        if (status != OrderItemStatus.PROCESSING) {
            throw new ItemNotEditableException("La unidad solo es editable en estado PROCESSING, actual: " + status);
        }
        this.sku = sku;
        this.externalItemId = externalItemId;
        this.unitPrice = unitPrice;
    }

    public boolean consumesQuota() {
        return status != OrderItemStatus.CANCELLED && importerType != ImporterType.UNASSIGNED;
    }

    private void ensureImporterChangeAllowed() {
        if (!status.isBeforeReadyToShip()) {
            throw new ImporterChangeNotAllowedException(
                    "El importador solo se puede cambiar antes de READY_TO_SHIP, actual: " + status);
        }
    }
}
