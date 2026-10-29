package com.example.msinventory.domain.model;

import com.example.msinventory.domain.exception.AllocationConflictException;
import com.example.msinventory.domain.exception.InvalidUnitTransitionException;
import com.example.msinventory.domain.exception.SkuMismatchException;
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
    private Instant createdAt;
    private Instant updatedAt;

    public static InventoryUnit newUnit(String sku, String conditionCode, Warehouse warehouse,
                                        InventoryUnitStatus status, BigDecimal unitCost, String costCurrency,
                                        EntryType entryType, String invoiceNumber, String supplierPurchaseId,
                                        String sourceReference, LocalDate expectedArrivalDate,
                                        LocalDate supplierReturnDeadline, Long entryId) {
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
                .arrivedAt(status == InventoryUnitStatus.AVAILABLE ? now : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** @return true si cambio de estado; false si ya estaba disponible (idempotente). */
    public boolean arrive() {
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
