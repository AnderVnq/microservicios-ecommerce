package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.ImporterChangeNotAllowedException;
import com.example.mspedidos.domain.exception.ImporterRequiredException;
import com.example.mspedidos.domain.exception.InvalidStatusTransitionException;
import com.example.mspedidos.domain.exception.ItemNotEditableException;
import com.example.mspedidos.domain.exception.UndoNotAllowedException;
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
    private Long inventoryUnitId;
    private boolean inventoryReleasePending;
    private Long pendingReleaseUnitId;
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
        if (isInPostAllocationState()) {
            this.inventoryReleasePending = true;
            this.pendingReleaseUnitId = inventoryUnitId;
        }
        this.status = OrderItemStatus.CANCELLED;
    }

    /** Cubre la unidad con stock de inventario (solo desde PROCESSING y sin cobertura previa). */
    public void coverFromInventory(Long inventoryUnitId, Warehouse warehouse) {
        if (warehouse == null) {
            throw new IllegalArgumentException("El almacen es obligatorio");
        }
        if (status != OrderItemStatus.PROCESSING || this.inventoryUnitId != null) {
            throw new InvalidStatusTransitionException(
                    "Solo una unidad en PROCESSING sin cobertura previa puede cubrirse desde inventario, actual: "
                            + status);
        }
        this.inventoryUnitId = inventoryUnitId;
        this.warehouse = warehouse;
        if (warehouse == Warehouse.LIMA) {
            this.status = OrderItemStatus.RECEIVED_LIMA;
            this.importerType = ImporterType.NOT_REQUIRED;
            this.familyMemberId = null;
            this.importerDocument = null;
        } else {
            this.status = OrderItemStatus.RECEIVED_AT_WAREHOUSE;
        }
    }

    /** La unidad sigue en el estado en que la dejo la cobertura de inventario. */
    public boolean isInPostAllocationState() {
        if (inventoryUnitId == null) {
            return false;
        }
        return (status == OrderItemStatus.RECEIVED_AT_WAREHOUSE && warehouse == Warehouse.USA)
                || (status == OrderItemStatus.RECEIVED_LIMA && warehouse == Warehouse.LIMA);
    }

    /** Deshace la cobertura; devuelve la unidad de inventario que queda pendiente de liberar. */
    public Long undoInventoryCoverage() {
        if (!isInPostAllocationState()) {
            throw new UndoNotAllowedException(
                    "La cobertura de inventario solo se puede deshacer mientras la unidad no haya avanzado, actual: "
                            + status);
        }
        Long old = inventoryUnitId;
        this.status = OrderItemStatus.PROCESSING;
        this.warehouse = null;
        this.pendingReleaseUnitId = old;
        this.inventoryUnitId = null;
        this.inventoryReleasePending = true;
        if (importerType == ImporterType.NOT_REQUIRED) {
            this.importerType = ImporterType.UNASSIGNED;
        }
        return old;
    }

    public void markReleased() {
        this.inventoryReleasePending = false;
        this.pendingReleaseUnitId = null;
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
        return status != OrderItemStatus.CANCELLED
                && importerType != ImporterType.UNASSIGNED
                && importerType != ImporterType.NOT_REQUIRED;
    }

    private void ensureImporterChangeAllowed() {
        if (importerType == ImporterType.NOT_REQUIRED) {
            throw new ImporterChangeNotAllowedException(
                    "La unidad cubierta desde inventario en Lima no requiere importador");
        }
        if (!status.isBeforeReadyToShip()) {
            throw new ImporterChangeNotAllowedException(
                    "El importador solo se puede cambiar antes de READY_TO_SHIP, actual: " + status);
        }
    }
}
