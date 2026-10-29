package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.OrderRepositoryPort;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({OrderRepositoryAdapter.class, OrderItemRepositoryAdapter.class, StatusChangeRequestRepositoryAdapter.class})
class OrderItemRepositoryAdapterTest {

    private static final ImporterDocument DNI_111 = new ImporterDocument("DNI", "111");
    private static final ImporterDocument DNI_222 = new ImporterDocument("DNI", "222");

    @Autowired
    OrderRepositoryPort orders;
    @Autowired
    OrderItemRepositoryPort items;
    @Autowired
    StatusChangeRequestRepositoryPort requests;

    private Order order(String externalId, Long marketId, LocalDateTime orderDate) {
        return orders.save(Order.builder()
                .orderNumber("N-" + externalId)
                .externalOrderId(externalId)
                .marketId(marketId)
                .holderId(1L)
                .orderDate(orderDate)
                .itemsTotal(BigDecimal.ZERO)
                .build());
    }

    private OrderItem item(Long orderId, ImporterType type, ImporterDocument doc, OrderItemStatus status) {
        return items.save(OrderItem.builder()
                .orderId(orderId)
                .sku("SKU")
                .externalItemId("L1")
                .unitPrice(new BigDecimal("10.00"))
                .status(status)
                .importerType(type)
                .importerDocument(doc)
                .build());
    }

    @Test
    void countQuotaUnits_countsAssignedNonCancelledForDocument() {
        Order o = order("EXT-1", 1L, LocalDateTime.of(2026, 5, 1, 10, 0));
        item(o.getId(), ImporterType.HOLDER, DNI_111, OrderItemStatus.PROCESSING);
        item(o.getId(), ImporterType.HOLDER, DNI_111, OrderItemStatus.PROCESSING);
        item(o.getId(), ImporterType.HOLDER, DNI_111, OrderItemStatus.CANCELLED);
        item(o.getId(), ImporterType.UNASSIGNED, null, OrderItemStatus.PROCESSING);
        item(o.getId(), ImporterType.HOLDER, DNI_222, OrderItemStatus.PROCESSING);

        assertThat(items.countQuotaUnits(DNI_111, 2026, null)).isEqualTo(2);
    }

    @Test
    void usedUnits_splitsByCalendarYear() {
        Order o2026 = order("EXT-A", 1L, LocalDateTime.of(2026, 12, 31, 23, 59));
        Order o2027 = order("EXT-B", 1L, LocalDateTime.of(2027, 1, 1, 0, 0));
        item(o2026.getId(), ImporterType.HOLDER, DNI_111, OrderItemStatus.PROCESSING);
        item(o2027.getId(), ImporterType.HOLDER, DNI_111, OrderItemStatus.PROCESSING);

        assertThat(items.countQuotaUnits(DNI_111, 2026, null)).isEqualTo(1);
        assertThat(items.countQuotaUnits(DNI_111, 2027, null)).isEqualTo(1);
    }

    @Test
    void countQuotaUnits_excludesGivenItem() {
        Order o = order("EXT-1", 1L, LocalDateTime.of(2026, 5, 1, 10, 0));
        OrderItem first = item(o.getId(), ImporterType.HOLDER, DNI_111, OrderItemStatus.PROCESSING);
        item(o.getId(), ImporterType.FAMILY_MEMBER, DNI_111, OrderItemStatus.PROCESSING);

        assertThat(items.countQuotaUnits(DNI_111, 2026, first.getId())).isEqualTo(1);
    }

    @Test
    void duplicateExternalOrderId_sameMarket_isDetected() {
        order("EXT-1", 1L, LocalDateTime.of(2026, 5, 1, 10, 0));

        assertThat(orders.existsByMarketIdAndExternalOrderId(1L, "EXT-1")).isTrue();
        assertThat(orders.existsByMarketIdAndExternalOrderId(2L, "EXT-1")).isFalse();
    }

    @Test
    void save_roundTripsImporterDocumentAndFindsByOrder() {
        Order o = order("EXT-1", 1L, LocalDateTime.of(2026, 5, 1, 10, 0));
        OrderItem saved = item(o.getId(), ImporterType.HOLDER, DNI_111, OrderItemStatus.PROCESSING);
        item(o.getId(), ImporterType.UNASSIGNED, null, OrderItemStatus.PROCESSING);

        OrderItem found = items.findByIdAndOrderId(saved.getId(), o.getId()).orElseThrow();
        assertThat(found.getImporterDocument()).isEqualTo(DNI_111);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(items.findByIdAndOrderId(saved.getId(), 999L)).isEmpty();
        assertThat(items.findByOrderId(o.getId())).hasSize(2)
                .anyMatch(i -> i.getImporterDocument() == null);
    }

    @Test
    void notRequiredUnitsDoNotCount() {
        Order o = order("EXT-NR", 1L, LocalDateTime.of(2026, 5, 1, 10, 0));
        item(o.getId(), ImporterType.NOT_REQUIRED, DNI_111, OrderItemStatus.RECEIVED_LIMA);
        item(o.getId(), ImporterType.HOLDER, DNI_111, OrderItemStatus.PROCESSING);

        assertThat(items.countQuotaUnits(DNI_111, 2026, null)).isEqualTo(1);
    }

    @Test
    void inventoryFieldsRoundTrip() {
        Order o = order("EXT-INV", 1L, LocalDateTime.of(2026, 5, 1, 10, 0));
        OrderItem saved = items.save(OrderItem.builder().orderId(o.getId()).sku("S").unitPrice(BigDecimal.ONE)
                .status(OrderItemStatus.CANCELLED).importerType(ImporterType.NOT_REQUIRED)
                .inventoryUnitId(5L).inventoryReleasePending(true).pendingReleaseUnitId(5L).build());
        OrderItem found = items.findByOrderId(o.getId()).get(0);
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getInventoryUnitId()).isEqualTo(5L);
        assertThat(found.isInventoryReleasePending()).isTrue();
        assertThat(found.getPendingReleaseUnitId()).isEqualTo(5L);
        assertThat(items.save(item(o.getId(), ImporterType.UNASSIGNED, null, OrderItemStatus.PROCESSING))
                .isInventoryReleasePending()).isFalse();
    }

    private OrderItem pendingItem(Long orderId, boolean pending) {
        return items.save(OrderItem.builder().orderId(orderId).sku("S").unitPrice(BigDecimal.ONE)
                .status(OrderItemStatus.CANCELLED).importerType(ImporterType.NOT_REQUIRED).inventoryUnitId(9L)
                .inventoryReleasePending(pending).pendingReleaseUnitId(pending ? 9L : null).build());
    }

    @Test
    void findReleasePending_returnsOnlyFlaggedUnitsById() {
        Order o = order("EXT-REL", 1L, LocalDateTime.of(2026, 5, 1, 10, 0));
        OrderItem first = pendingItem(o.getId(), true);
        pendingItem(o.getId(), false);
        OrderItem second = pendingItem(o.getId(), true);

        assertThat(items.findReleasePending()).extracting(OrderItem::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void search_filtersByReleasePendingOrReturnsAll() {
        Order o = order("EXT-SRCH", 1L, LocalDateTime.of(2026, 5, 1, 10, 0));
        OrderItem pending = pendingItem(o.getId(), true);
        OrderItem clean = pendingItem(o.getId(), false);

        assertThat(items.search(true)).extracting(OrderItem::getId).containsExactly(pending.getId());
        assertThat(items.search(false)).extracting(OrderItem::getId).containsExactly(clean.getId());
        assertThat(items.search(null)).extracting(OrderItem::getId).containsExactly(pending.getId(), clean.getId());
    }

    private Order orderWithDeadline(String ext, LocalDateTime deadline, LocalDateTime orderDate) {
        return orders.save(Order.builder().orderNumber("N-" + ext).externalOrderId(ext).marketId(1L).holderId(1L)
                .orderDate(orderDate).shippingDeadline(deadline).itemsTotal(BigDecimal.ZERO).build());
    }

    private OrderItem processing(Long orderId, String sku) {
        return items.save(OrderItem.builder().orderId(orderId).sku(sku).unitPrice(BigDecimal.ONE)
                .status(OrderItemStatus.PROCESSING).importerType(ImporterType.UNASSIGNED).build());
    }

    @Test
    void findCoverableIdsByUrgency_deadlineAscNullsLastThenOrderDateThenId() {
        LocalDateTime d1 = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime d2 = LocalDateTime.of(2026, 7, 1, 0, 0);
        Order noDeadlineOld = orderWithDeadline("E1", null, LocalDateTime.of(2026, 1, 1, 0, 0));
        Order late = orderWithDeadline("E2", d2, LocalDateTime.of(2026, 1, 1, 0, 0));
        Order earlyNewer = orderWithDeadline("E3", d1, LocalDateTime.of(2026, 3, 1, 0, 0));
        Order earlyOlder = orderWithDeadline("E4", d1, LocalDateTime.of(2026, 2, 1, 0, 0));
        OrderItem a = processing(noDeadlineOld.getId(), "S");
        OrderItem b = processing(late.getId(), "S");
        OrderItem c = processing(earlyNewer.getId(), "S");
        OrderItem d1i = processing(earlyOlder.getId(), "S");
        OrderItem d2i = processing(earlyOlder.getId(), "S");

        assertThat(items.findCoverableIdsByUrgency("S", 10)).containsExactly(
                d1i.getId(), d2i.getId(), c.getId(), b.getId(), a.getId());
        assertThat(items.findCoverableIdsByUrgency("S", 2)).containsExactly(d1i.getId(), d2i.getId());
        assertThat(items.findCoverableIdsByUrgency("OTHER", 10)).isEmpty();
    }

    @Test
    void findCoverableIdsByUrgency_skipsCoveredPendingAndRequestBlocked() {
        Order o = orderWithDeadline("E1", null, LocalDateTime.of(2026, 1, 1, 0, 0));
        OrderItem free = processing(o.getId(), "S");
        items.save(OrderItem.builder().orderId(o.getId()).sku("S").unitPrice(BigDecimal.ONE)
                .status(OrderItemStatus.PROCESSING).importerType(ImporterType.UNASSIGNED).purchaseId(5L).build());
        items.save(OrderItem.builder().orderId(o.getId()).sku("S").unitPrice(BigDecimal.ONE)
                .status(OrderItemStatus.PROCESSING).importerType(ImporterType.UNASSIGNED).inventoryUnitId(8L).build());
        items.save(OrderItem.builder().orderId(o.getId()).sku("S").unitPrice(BigDecimal.ONE)
                .status(OrderItemStatus.PROCESSING).importerType(ImporterType.UNASSIGNED)
                .inventoryReleasePending(true).build());
        items.save(OrderItem.builder().orderId(o.getId()).sku("S").unitPrice(BigDecimal.ONE)
                .status(OrderItemStatus.PURCHASED).importerType(ImporterType.UNASSIGNED).build());
        OrderItem blocked = processing(o.getId(), "S");
        requests.save(StatusChangeRequest.openAllocationUndo(blocked.getId(), 1L, "m", "ana"));
        OrderItem decided = processing(o.getId(), "S");
        StatusChangeRequest done = requests.save(
                StatusChangeRequest.openAllocationUndo(decided.getId(), 2L, "m", "ana"));
        done.reject("bob", "no");
        requests.save(done);
        // una solicitud de compra (sin order_item_id) no excluye unidades
        requests.save(StatusChangeRequest.openNotReceived(5L, 1, "x", "ana"));

        assertThat(items.findCoverableIdsByUrgency("S", 10)).containsExactly(free.getId(), decided.getId());
    }

    @Test
    void orderByUrgency_sorts() {
        Order none = orderWithDeadline("E1", null, LocalDateTime.of(2026, 1, 1, 0, 0));
        Order early = orderWithDeadline("E2", LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 2, 1, 0, 0));
        OrderItem a = processing(none.getId(), "S");
        OrderItem b = processing(early.getId(), "S");
        OrderItem c = processing(early.getId(), "S");

        assertThat(items.orderByUrgency(List.of(a.getId(), c.getId(), b.getId())))
                .containsExactly(b.getId(), c.getId(), a.getId());
        assertThat(items.orderByUrgency(List.of())).isEmpty();
    }

    @Test
    void purchaseIdRoundTripsAndLocksByIds() {
        Order o = orderWithDeadline("E1", null, LocalDateTime.of(2026, 1, 1, 0, 0));
        OrderItem a = items.save(OrderItem.builder().orderId(o.getId()).sku("S").unitPrice(BigDecimal.ONE)
                .status(OrderItemStatus.PURCHASED).importerType(ImporterType.UNASSIGNED).purchaseId(9L).build());
        OrderItem b = processing(o.getId(), "S");

        assertThat(items.findById(a.getId()).orElseThrow().getPurchaseId()).isEqualTo(9L);
        assertThat(items.findByPurchaseIdForUpdate(9L)).extracting(OrderItem::getId).containsExactly(a.getId());
        assertThat(items.findByIdsForUpdate(List.of(b.getId(), a.getId())))
                .extracting(OrderItem::getId).containsExactly(a.getId(), b.getId());
        assertThat(items.findByIdsForUpdate(List.of())).isEmpty();
    }
}
