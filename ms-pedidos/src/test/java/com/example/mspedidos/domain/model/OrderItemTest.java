package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.ImporterChangeNotAllowedException;
import com.example.mspedidos.domain.exception.ImporterRequiredException;
import com.example.mspedidos.domain.exception.InvalidStatusTransitionException;
import com.example.mspedidos.domain.exception.ItemNotEditableException;
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
        item.changeStatus(OrderItemStatus.PURCHASED, null, null);
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
}
