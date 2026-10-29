package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.OrderRepositoryPort;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({OrderRepositoryAdapter.class, OrderItemRepositoryAdapter.class})
class OrderItemRepositoryAdapterTest {

    private static final ImporterDocument DNI_111 = new ImporterDocument("DNI", "111");
    private static final ImporterDocument DNI_222 = new ImporterDocument("DNI", "222");

    @Autowired
    OrderRepositoryPort orders;
    @Autowired
    OrderItemRepositoryPort items;

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
}
