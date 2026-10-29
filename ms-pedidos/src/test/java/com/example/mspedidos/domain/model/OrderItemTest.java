package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.ImporterChangeNotAllowedException;
import com.example.mspedidos.domain.exception.ImporterRequiredException;
import com.example.mspedidos.domain.exception.InvalidStatusTransitionException;
import com.example.mspedidos.domain.exception.ItemNotEditableException;
import com.example.mspedidos.domain.exception.UndoNotAllowedException;
import com.example.mspedidos.domain.exception.UsePurchaseFlowException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    private static final ImporterDocument DOC = new ImporterDocument("DNI", "12345678");

    private OrderItem unit() {
        return OrderItem.newUnit("SKU-1", "EXT-1", new BigDecimal("10.00"));
    }

    private OrderItem unitAt(OrderItemStatus status, ImporterType importer) {
        return OrderItem.builder().status(status).importerType(importer)
                .importerDocument(importer == ImporterType.UNASSIGNED ? null : DOC).build();
    }

    @Test
    void newUnit_startsProcessingUnassigned() {
        OrderItem item = unit();
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(item.getImporterType()).isEqualTo(ImporterType.UNASSIGNED);
        assertThat(item.consumesQuota()).isFalse();
    }

    @Test
    void attachTo_setsOrderOnce() {
        OrderItem item = unit();
        item.attachTo(5L);
        item.attachTo(5L);
        assertThat(item.getOrderId()).isEqualTo(5L);
        assertThatThrownBy(() -> item.attachTo(6L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void advancesOnlyToNextStatus() {
        OrderItem item = unit();
        item.coverByPurchase(1L);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThatThrownBy(() -> unit().changeStatus(OrderItemStatus.SHIPPED, null, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void changeStatusOnTerminalItemIsRejected() {
        OrderItem received = unitAt(OrderItemStatus.CUSTOMER_RECEIVED, ImporterType.HOLDER);
        assertThatThrownBy(() -> received.changeStatus(OrderItemStatus.CANCELLED, null, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
        OrderItem cancelled = unitAt(OrderItemStatus.CANCELLED, ImporterType.HOLDER);
        assertThatThrownBy(() -> cancelled.changeStatus(OrderItemStatus.PROCESSING, null, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(cancelled.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
    }

    @Test
    void changeStatusToCancelledIsRejected() {
        assertThatThrownBy(() -> unit().changeStatus(OrderItemStatus.CANCELLED, null, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void customsHoldIsOptional() {
        OrderItem direct = unitAt(OrderItemStatus.ARRIVED_LIMA, ImporterType.HOLDER);
        direct.changeStatus(OrderItemStatus.RECEIVED_LIMA, null, null);
        assertThat(direct.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_LIMA);

        OrderItem held = unitAt(OrderItemStatus.ARRIVED_LIMA, ImporterType.HOLDER);
        held.changeStatus(OrderItemStatus.CUSTOMS_HOLD, null, null);
        held.changeStatus(OrderItemStatus.RECEIVED_LIMA, null, null);
        assertThat(held.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_LIMA);
    }

    @Test
    void receivedAtWarehouseRequiresWarehouse() {
        assertThatThrownBy(() -> unitAt(OrderItemStatus.PURCHASED, ImporterType.UNASSIGNED)
                .changeStatus(OrderItemStatus.RECEIVED_AT_WAREHOUSE, null, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
        OrderItem item = unitAt(OrderItemStatus.PURCHASED, ImporterType.UNASSIGNED);
        item.changeStatus(OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA, null);
        assertThat(item.getWarehouse()).isEqualTo(Warehouse.USA);
    }

    @Test
    void deliveredRequiresDeliveredTo() {
        assertThatThrownBy(() -> unitAt(OrderItemStatus.DISPATCHED, ImporterType.HOLDER)
                .changeStatus(OrderItemStatus.DELIVERED, null, null))
                .isInstanceOf(InvalidStatusTransitionException.class);
        OrderItem item = unitAt(OrderItemStatus.DISPATCHED, ImporterType.HOLDER);
        item.changeStatus(OrderItemStatus.DELIVERED, null, DeliveredTo.CUSTOMER);
        assertThat(item.getDeliveredTo()).isEqualTo(DeliveredTo.CUSTOMER);
    }

    @Test
    void standby_unassignedCannotReachReadyToShip() {
        OrderItem item = unitAt(OrderItemStatus.RECEIVED_AT_WAREHOUSE, ImporterType.UNASSIGNED);
        assertThatThrownBy(() -> item.changeStatus(OrderItemStatus.READY_TO_SHIP, null, null))
                .isInstanceOf(ImporterRequiredException.class);
        item.assignHolder(DOC);
        item.changeStatus(OrderItemStatus.READY_TO_SHIP, null, null);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.READY_TO_SHIP);
    }

    @Test
    void importerChangeOnlyBeforeReadyToShip() {
        OrderItem item = unitAt(OrderItemStatus.READY_TO_SHIP, ImporterType.HOLDER);
        assertThatThrownBy(() -> item.assignFamilyMember(7L, DOC))
                .isInstanceOf(ImporterChangeNotAllowedException.class);
        assertThatThrownBy(() -> item.assignHolder(DOC))
                .isInstanceOf(ImporterChangeNotAllowedException.class);
    }

    @Test
    void assignFamilyMemberBeforeReadyToShip_consumesQuota() {
        OrderItem item = unit();
        item.assignFamilyMember(7L, DOC);
        assertThat(item.getImporterType()).isEqualTo(ImporterType.FAMILY_MEMBER);
        assertThat(item.getFamilyMemberId()).isEqualTo(7L);
        assertThat(item.consumesQuota()).isTrue();
        item.assignHolder(DOC);
        assertThat(item.getFamilyMemberId()).isNull();
    }

    @Test
    void cancelFromNonTerminal_andNotFromTerminal() {
        OrderItem item = unitAt(OrderItemStatus.PURCHASED, ImporterType.HOLDER);
        assertThat(item.consumesQuota()).isTrue();
        item.cancel();
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(item.consumesQuota()).isFalse();
        assertThatThrownBy(item::cancel).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void updateDetailsOnlyInProcessing() {
        OrderItem item = unit();
        item.updateDetails("SKU-2", "EXT-2", new BigDecimal("5.00"));
        assertThat(item.getSku()).isEqualTo("SKU-2");
        OrderItem purchased = unitAt(OrderItemStatus.PURCHASED, ImporterType.UNASSIGNED);
        assertThatThrownBy(() -> purchased.updateDetails("X", "Y", BigDecimal.ONE))
                .isInstanceOf(ItemNotEditableException.class);
    }

    @Test
    void importerDocumentRequiresNonBlankValues() {
        assertThatThrownBy(() -> new ImporterDocument("", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImporterDocument("DNI", null)).isInstanceOf(IllegalArgumentException.class);
    }

    private OrderItem processingWithImporter() {
        OrderItem item = unit();
        item.assignHolder(DOC);
        return item;
    }

    @Test
    void coverFromInventory_usa_receivedAtWarehouseKeepsImporter() {
        OrderItem item = processingWithImporter();
        item.coverFromInventory(7L, Warehouse.USA);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(item.getWarehouse()).isEqualTo(Warehouse.USA);
        assertThat(item.getInventoryUnitId()).isEqualTo(7L);
        assertThat(item.getImporterType()).isEqualTo(ImporterType.HOLDER);
        assertThat(item.getImporterDocument()).isEqualTo(DOC);
        assertThat(item.consumesQuota()).isTrue();
        assertThat(item.isInPostAllocationState()).isTrue();
    }

    @Test
    void coverFromInventory_lima_receivedLimaNotRequired() {
        OrderItem item = unit();
        item.assignFamilyMember(3L, DOC);
        item.coverFromInventory(8L, Warehouse.LIMA);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_LIMA);
        assertThat(item.getWarehouse()).isEqualTo(Warehouse.LIMA);
        assertThat(item.getImporterType()).isEqualTo(ImporterType.NOT_REQUIRED);
        assertThat(item.getFamilyMemberId()).isNull();
        assertThat(item.getImporterDocument()).isNull();
        assertThat(item.consumesQuota()).isFalse();
        assertThat(item.isInPostAllocationState()).isTrue();
    }

    @Test
    void coverFromInventory_notProcessing_throws() {
        OrderItem purchased = unitAt(OrderItemStatus.PURCHASED, ImporterType.HOLDER);
        assertThatThrownBy(() -> purchased.coverFromInventory(1L, Warehouse.USA))
                .isInstanceOf(InvalidStatusTransitionException.class);
        OrderItem covered = processingWithImporter();
        covered.coverFromInventory(1L, Warehouse.USA);
        OrderItem alreadyCovered = OrderItem.builder().status(OrderItemStatus.PROCESSING)
                .importerType(ImporterType.UNASSIGNED).inventoryUnitId(2L).build();
        assertThatThrownBy(() -> alreadyCovered.coverFromInventory(3L, Warehouse.USA))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void notRequired_passesStandby_andCannotBeReassigned() {
        OrderItem item = unitAt(OrderItemStatus.RECEIVED_LIMA, ImporterType.NOT_REQUIRED);
        item.changeStatus(OrderItemStatus.DISPATCHED, null, null);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.DISPATCHED);

        OrderItem early = unitAt(OrderItemStatus.PROCESSING, ImporterType.NOT_REQUIRED);
        assertThat(early.consumesQuota()).isFalse();
        assertThatThrownBy(() -> early.assignHolder(DOC)).isInstanceOf(ImporterChangeNotAllowedException.class);
        assertThatThrownBy(() -> early.assignFamilyMember(1L, DOC))
                .isInstanceOf(ImporterChangeNotAllowedException.class);
    }

    @Test
    void undo_fromLima_backToProcessingUnassignedPending() {
        OrderItem item = unit();
        item.coverFromInventory(9L, Warehouse.LIMA);
        Long old = item.undoInventoryCoverage();
        assertThat(old).isEqualTo(9L);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(item.getWarehouse()).isNull();
        assertThat(item.getInventoryUnitId()).isNull();
        assertThat(item.isInventoryReleasePending()).isTrue();
        assertThat(item.getPendingReleaseUnitId()).isEqualTo(9L);
        assertThat(item.getImporterType()).isEqualTo(ImporterType.UNASSIGNED);

        item.markReleased();
        assertThat(item.isInventoryReleasePending()).isFalse();
        assertThat(item.getPendingReleaseUnitId()).isNull();
    }

    @Test
    void undo_fromUsa_keepsImporter() {
        OrderItem item = processingWithImporter();
        item.coverFromInventory(9L, Warehouse.USA);
        item.undoInventoryCoverage();
        assertThat(item.getImporterType()).isEqualTo(ImporterType.HOLDER);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
    }

    @Test
    void undo_afterAdvancing_throws() {
        OrderItem item = processingWithImporter();
        item.coverFromInventory(9L, Warehouse.USA);
        item.changeStatus(OrderItemStatus.READY_TO_SHIP, null, null);
        assertThat(item.isInPostAllocationState()).isFalse();
        assertThatThrownBy(item::undoInventoryCoverage).isInstanceOf(UndoNotAllowedException.class);
        assertThatThrownBy(() -> unit().undoInventoryCoverage()).isInstanceOf(UndoNotAllowedException.class);
    }

    @Test
    void cancel_postAllocation_marksReleasePending() {
        OrderItem item = unit();
        item.coverFromInventory(11L, Warehouse.LIMA);
        item.cancel();
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(item.isInventoryReleasePending()).isTrue();
        assertThat(item.getPendingReleaseUnitId()).isEqualTo(11L);
        assertThat(item.getInventoryUnitId()).isEqualTo(11L);
    }

    @Test
    void cancel_withoutAllocation_noReleasePending() {
        OrderItem item = unit();
        item.cancel();
        assertThat(item.isInventoryReleasePending()).isFalse();
        assertThat(item.getPendingReleaseUnitId()).isNull();
    }

    @Test
    void coverFromInventory_nullWarehouse_throws() {
        OrderItem item = unit();
        assertThatThrownBy(() -> item.coverFromInventory(1L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getInventoryUnitId()).isNull();
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
    }

    private OrderItem purchased(Long purchaseId, Long unitId) {
        return OrderItem.builder().status(OrderItemStatus.PURCHASED).importerType(ImporterType.UNASSIGNED)
                .purchaseId(purchaseId).inventoryUnitId(unitId).build();
    }

    @Test
    void coverByPurchase_onlyProcessingUncovered() {
        OrderItem item = unit();
        item.coverByPurchase(5L);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(item.getPurchaseId()).isEqualTo(5L);
        assertThatThrownBy(() -> item.coverByPurchase(6L)).isInstanceOf(InvalidStatusTransitionException.class);
        OrderItem withUnit = OrderItem.builder().status(OrderItemStatus.PROCESSING).inventoryUnitId(3L).build();
        assertThatThrownBy(() -> withUnit.coverByPurchase(6L)).isInstanceOf(InvalidStatusTransitionException.class);
        OrderItem pending = OrderItem.builder().status(OrderItemStatus.PROCESSING)
                .inventoryReleasePending(true).build();
        assertThatThrownBy(() -> pending.coverByPurchase(6L)).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void linkPurchaseUnit_onlyWhenPurchasedWithPurchase() {
        OrderItem item = unit();
        item.coverByPurchase(5L);
        item.linkPurchaseUnit(11L);
        assertThat(item.getInventoryUnitId()).isEqualTo(11L);
        OrderItem plain = unit();
        assertThatThrownBy(() -> plain.linkPurchaseUnit(1L)).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void receiveFromPurchase_setsUsa() {
        OrderItem item = purchased(5L, 11L);
        item.receiveFromPurchase();
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(item.getWarehouse()).isEqualTo(Warehouse.USA);
        assertThatThrownBy(() -> purchased(null, null).receiveFromPurchase())
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void revertNotReceived_clearsPurchaseAndUnit() {
        OrderItem item = purchased(5L, 11L);
        assertThat(item.revertNotReceived()).isEqualTo(11L);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(item.getPurchaseId()).isNull();
        assertThat(item.getInventoryUnitId()).isNull();
        assertThat(item.isInventoryReleasePending()).isFalse();
        OrderItem noUnit = purchased(5L, null);
        assertThat(noUnit.revertNotReceived()).isNull();
        assertThatThrownBy(() -> unit().revertNotReceived()).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void cancel_purchasedWithUnit_marksReleasePending() {
        OrderItem item = purchased(5L, 11L);
        item.cancel();
        assertThat(item.isInventoryReleasePending()).isTrue();
        assertThat(item.getPendingReleaseUnitId()).isEqualTo(11L);
        OrderItem received = purchased(5L, 12L);
        received.receiveFromPurchase();
        assertThat(received.releasesInventoryOnCancel()).isTrue();
    }

    @Test
    void cancel_purchasedWithoutUnit_noReleasePending() {
        OrderItem item = purchased(5L, null);
        item.cancel();
        assertThat(item.isInventoryReleasePending()).isFalse();
        assertThat(item.getPendingReleaseUnitId()).isNull();
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
    }

    @Test
    void canUndoAllocation_falseForPurchaseCovered() {
        OrderItem item = purchased(5L, 11L);
        item.receiveFromPurchase();
        assertThat(item.isInPostAllocationState()).isTrue();
        assertThat(item.canUndoAllocation()).isFalse();
        assertThatThrownBy(item::undoInventoryCoverage).isInstanceOf(UndoNotAllowedException.class);
        OrderItem allocated = OrderItem.builder().status(OrderItemStatus.RECEIVED_AT_WAREHOUSE)
                .warehouse(Warehouse.USA).inventoryUnitId(2L).importerType(ImporterType.UNASSIGNED).build();
        assertThat(allocated.canUndoAllocation()).isTrue();
    }

    @Test
    void manualPatchToPurchased_usePurchaseFlow() {
        assertThatThrownBy(() -> unit().changeStatus(OrderItemStatus.PURCHASED, null, null))
                .isInstanceOf(UsePurchaseFlowException.class);
    }

    @Test
    void manualReceive_withPurchase_usePurchaseFlow_withoutPurchase_ok() {
        OrderItem withPurchase = purchased(5L, 11L);
        assertThatThrownBy(() -> withPurchase.changeStatus(OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA, null))
                .isInstanceOf(UsePurchaseFlowException.class);
        OrderItem legacy = purchased(null, null);
        legacy.changeStatus(OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA, null);
        assertThat(legacy.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
    }

    @Test
    void linkPurchaseUnit_afterArrival_allowed_cancelledRejected() {
        OrderItem received = purchased(5L, null);
        received.receiveFromPurchase();
        received.linkPurchaseUnit(11L);
        assertThat(received.getInventoryUnitId()).isEqualTo(11L);
        OrderItem cancelled = purchased(5L, null);
        cancelled.cancel();
        assertThatThrownBy(() -> cancelled.linkPurchaseUnit(12L)).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void flagReleaseOfUnlinkedPurchaseUnit_onlyCancelledWithoutUnit() {
        OrderItem cancelled = purchased(5L, null);
        cancelled.cancel();
        cancelled.flagReleaseOfUnlinkedPurchaseUnit(11L);
        assertThat(cancelled.isInventoryReleasePending()).isTrue();
        assertThat(cancelled.getPendingReleaseUnitId()).isEqualTo(11L);
        assertThat(cancelled.getInventoryUnitId()).isNull();

        assertThatThrownBy(() -> purchased(5L, null).flagReleaseOfUnlinkedPurchaseUnit(11L))
                .isInstanceOf(InvalidStatusTransitionException.class);
        OrderItem linked = purchased(5L, 3L);
        linked.cancel();
        assertThatThrownBy(() -> linked.flagReleaseOfUnlinkedPurchaseUnit(11L))
                .isInstanceOf(InvalidStatusTransitionException.class);
        // ya pendiente de liberar otra unidad: no se pisa la marca
        assertThatThrownBy(() -> cancelled.flagReleaseOfUnlinkedPurchaseUnit(12L))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> cancelled.flagReleaseOfUnlinkedPurchaseUnit(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
