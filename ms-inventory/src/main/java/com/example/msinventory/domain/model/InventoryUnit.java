package com.example.msinventory.domain.model;

import com.example.msinventory.domain.exception.AllocationConflictException;
import com.example.msinventory.domain.exception.InvalidUnitTransitionException;
import com.example.msinventory.domain.exception.SkuMismatchException;
import com.example.msinventory.domain.exception.UsePurchaseFlowException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class InventoryUnit {

    private Long id;
    private String sku;
    private String conditionCode;
    private Warehouse warehouse;
    private InventoryUnitStatus status;
    private BigDecimal unitCost;
    private String costCurrency;
    private EntryType entryType;
    private String invoiceNumber;
    private String supplierPurchaseId;
    private Long supplierId;
    private String sourceReference;
    private LocalDate expectedArrivalDate;
    private Instant arrivedAt;
    private LocalDate supplierReturnDeadline;
    private Long allocatedOrderItemId;
    private Long entryId;
    private Long purchaseId;
    private Instant createdAt;
    private Instant updatedAt;

    public static InventoryUnit newUnit(String sku, String conditionCode, Warehouse warehouse,
                                        InventoryUnitStatus status, BigDecimal unitCost, String costCurrency,
                                        EntryType entryType, String invoiceNumber, String supplierPurchaseId,
                                        String sourceReference, LocalDate expectedArrivalDate,
                                        LocalDate supplierReturnDeadline, Long entryId) {
        return newUnit(sku, conditionCode, warehouse, status, unitCost, costCurrency, entryType, invoiceNumber,
                supplierPurchaseId, sourceReference, expectedArrivalDate, supplierReturnDeadline, entryId, null, null);
    }

    public static InventoryUnit newUnit(String sku, String conditionCode, Warehouse warehouse,
                                        InventoryUnitStatus status, BigDecimal unitCost, String costCurrency,
                                        EntryType entryType, String invoiceNumber, String supplierPurchaseId,
                                        String sourceReference, LocalDate expectedArrivalDate,
                                        LocalDate supplierReturnDeadline, Long entryId,
                                        Long supplierId, Long purchaseId) {
        if (status != InventoryUnitStatus.IN_TRANSIT && status != InventoryUnitStatus.AVAILABLE) {
            throw new IllegalArgumentException("Una unidad nueva solo puede iniciar EN TRANSITO o DISPONIBLE");
        }
        if (status == InventoryUnitStatus.IN_TRANSIT && expectedArrivalDate == null) {
            throw new IllegalArgumentException("La fecha estimada de llegada es obligatoria para unidades en transito");
        }
        Instant now = Instant.now();
        return InventoryUnit.builder()
                .sku(sku)
                .conditionCode(conditionCode)
                .warehouse(warehouse)
                .status(status)
                .unitCost(unitCost)
                .costCurrency(costCurrency)
                .entryType(entryType)
                .invoiceNumber(invoiceNumber)
                .supplierPurchaseId(supplierPurchaseId)
                .sourceReference(sourceReference)
                .expectedArrivalDate(expectedArrivalDate)
                .supplierReturnDeadline(supplierReturnDeadline)
                .entryId(entryId)
                .supplierId(supplierId)
                .purchaseId(purchaseId)
                .arrivedAt(status == InventoryUnitStatus.AVAILABLE ? now : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** @return true si cambio de estado; false si ya estaba disponible (idempotente). */
    public boolean arrive() {
        requireNotUnarrivedPurchaseUnit();
        if (status == InventoryUnitStatus.AVAILABLE) {
            return false;
        }
        if (status != InventoryUnitStatus.IN_TRANSIT) {
            throw invalidTransition(InventoryUnitStatus.AVAILABLE);
        }
        status = InventoryUnitStatus.AVAILABLE;
        arrivedAt = Instant.now();
        touch();
        return true;
    }

    /** @return el almacen de origen. */
    public Warehouse transferTo(Warehouse to, LocalDate expectedArrival) {
        requireNotUnarrivedPurchaseUnit();
        if (to == null || expectedArrival == null) {
            throw new IllegalArgumentException("El almacen destino y la fecha estimada de llegada son obligatorios");
        }
        if (!status.canMoveTo(InventoryUnitStatus.IN_TRANSIT)) {
            throw invalidTransition(InventoryUnitStatus.IN_TRANSIT);
        }
        if (to == warehouse) {
            throw new InvalidUnitTransitionException("La unidad ya esta en el almacen " + to);
        }
        Warehouse origin = warehouse;
        warehouse = to;
        status = InventoryUnitStatus.IN_TRANSIT;
        expectedArrivalDate = expectedArrival;
        arrivedAt = null;
        touch();
        return origin;
    }

    public void allocate(Long orderItemId, String orderItemSku) {
        if (sku == null || !sku.equals(orderItemSku)) {
            throw new SkuMismatchException("El SKU de la unidad (" + sku + ") no coincide con el del item (" + orderItemSku + ")");
        }
        if (!status.canMoveTo(InventoryUnitStatus.ALLOCATED)) {
            throw invalidTransition(InventoryUnitStatus.ALLOCATED);
        }
        status = InventoryUnitStatus.ALLOCATED;
        allocatedOrderItemId = orderItemId;
        touch();
    }

    public void release(Long orderItemId) {
        if (status != InventoryUnitStatus.ALLOCATED) {
            throw invalidTransition(InventoryUnitStatus.AVAILABLE);
        }
        if (allocatedOrderItemId == null || !allocatedOrderItemId.equals(orderItemId)) {
            throw new AllocationConflictException("La unidad esta asignada a otro item de pedido");
        }
        status = InventoryUnitStatus.AVAILABLE;
        allocatedOrderItemId = null;
        touch();
    }

    /** @return true si cambio de estado; false si ya estaba en el estado destino (idempotente). */
    public boolean exit(InventoryUnitStatus target) {
        if (target == InventoryUnitStatus.WRITTEN_OFF) {
            requireNotUnarrivedPurchaseUnit();
        }
        if (target != InventoryUnitStatus.RETURNED_TO_SUPPLIER
                && target != InventoryUnitStatus.WRITTEN_OFF
                && target != InventoryUnitStatus.ADJUSTED_OUT) {
            throw invalidTransition(target);
        }
        if (status == target) {
            return false;
        }
        if (!status.canMoveTo(target)) {
            throw invalidTransition(target);
        }
        status = target;
        touch();
        return true;
    }

    /** Unidad de compra a proveedor que aun no llega (en transito). */
    public boolean isUnarrivedPurchaseUnit() {
        return entryType == EntryType.SUPPLIER_PURCHASE && status == InventoryUnitStatus.IN_TRANSIT;
    }

    /** Reserva la unidad en transito para un item de pedido; idempotente para el mismo item. */
    public void reserve(Long orderItemId) {
        if (orderItemId == null) {
            throw new IllegalArgumentException("El item de pedido es obligatorio para reservar");
        }
        if (!isUnarrivedPurchaseUnit()) {
            throw new InvalidUnitTransitionException("Solo una unidad de compra en transito puede reservarse");
        }
        if (allocatedOrderItemId != null) {
            if (allocatedOrderItemId.equals(orderItemId)) {
                return;
            }
            throw new AllocationConflictException("La unidad ya esta reservada para otro item de pedido");
        }
        allocatedOrderItemId = orderItemId;
        touch();
    }

    /** @return true si libero la reserva; false si no estaba reservada. */
    public boolean releaseReservation(Long orderItemId) {
        if (status != InventoryUnitStatus.IN_TRANSIT || allocatedOrderItemId == null) {
            return false;
        }
        if (!allocatedOrderItemId.equals(orderItemId)) {
            throw new AllocationConflictException("La unidad esta reservada para otro item de pedido");
        }
        allocatedOrderItemId = null;
        touch();
        return true;
    }

    /** @return true si cambio de estado; false si la unidad de compra ya habia llegado (idempotente). */
    public boolean arriveFromPurchase() {
        if (!isUnarrivedPurchaseUnit()) {
            if (entryType == EntryType.SUPPLIER_PURCHASE && arrivedAt != null
                    && (status == InventoryUnitStatus.AVAILABLE || status == InventoryUnitStatus.ALLOCATED)) {
                return false;
            }
            throw new InvalidUnitTransitionException("La unidad no es una compra de proveedor en transito: " + status);
        }
        status = allocatedOrderItemId != null ? InventoryUnitStatus.ALLOCATED : InventoryUnitStatus.AVAILABLE;
        arrivedAt = Instant.now();
        touch();
        return true;
    }

    /** @return true si cambio de estado; false si ya estaba NOT_RECEIVED (idempotente). */
    public boolean markNotReceived() {
        if (status == InventoryUnitStatus.NOT_RECEIVED) {
            return false;
        }
        if (!isUnarrivedPurchaseUnit()) {
            throw invalidTransition(InventoryUnitStatus.NOT_RECEIVED);
        }
        status = InventoryUnitStatus.NOT_RECEIVED;
        allocatedOrderItemId = null;
        touch();
        return true;
    }

    private void requireNotUnarrivedPurchaseUnit() {
        if (isUnarrivedPurchaseUnit()) {
            throw new UsePurchaseFlowException("La unidad es de una compra a proveedor en transito: use el flujo de compras");
        }
    }

    public void updateDates(LocalDate expectedArrivalDate, LocalDate supplierReturnDeadline) {
        if (status == InventoryUnitStatus.IN_TRANSIT && expectedArrivalDate == null) {
            throw new IllegalArgumentException("La fecha estimada de llegada es obligatoria para unidades en transito");
        }
        this.expectedArrivalDate = expectedArrivalDate;
        this.supplierReturnDeadline = supplierReturnDeadline;
        touch();
    }

    private InvalidUnitTransitionException invalidTransition(InventoryUnitStatus target) {
        return new InvalidUnitTransitionException("Transicion de unidad no permitida: " + status + " -> " + target);
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}
