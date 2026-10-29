package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.PurchaseUseCase;
import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.application.port.in.RegisterPurchaseCommand;
import com.example.mspedidos.application.port.out.InventoryPort.CreatePurchaseEntry;
import com.example.mspedidos.application.port.out.InventoryPort.PurchaseEntryResult;
import com.example.mspedidos.application.port.out.InventorySyncState;
import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.application.port.out.PurchaseFilter;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.application.port.out.SupplierRepositoryPort;
import com.example.mspedidos.domain.exception.IdempotencyKeyReusedException;
import com.example.mspedidos.domain.exception.InvalidStatusTransitionException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.ItemSkuChangedException;
import com.example.mspedidos.domain.exception.PurchaseNotFoundException;
import com.example.mspedidos.domain.exception.StatusChangeRequestPendingException;
import com.example.mspedidos.domain.exception.SupplierInactiveException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.Supplier;
import com.example.mspedidos.domain.model.SupplierStatus;
import com.example.mspedidos.domain.model.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseServiceTest extends ServiceTestSupport {

    static final String SKU = "SKU-X";

    @Autowired
    PurchaseUseCase useCase;
    @Autowired
    SupplierRepositoryPort supplierRepository;
    @Autowired
    InventorySyncTaskRepositoryPort taskRepository;
    @Autowired
    StatusChangeRequestRepositoryPort requestRepository;

    Supplier supplier;

    @BeforeEach
    void cleanPurchasesAndStubInventory() {
        jdbc.update("delete from inventory_sync_tasks");
        jdbc.update("delete from purchase_arrivals");
        jdbc.update("delete from purchase_non_receipts");
        jdbc.update("delete from purchases");
        supplier = supplierRepository.save(Supplier.create("Prov " + uniqueExternalId(), "US", null, null));
        when(inventoryPort.isConditionActive("NEW")).thenReturn(true);
        // inventario reserva una unidad fisica por cada unidad de pedido: unitId = 1000 + orderItemId
        when(inventoryPort.createPurchaseEntry(anyString(), any())).thenAnswer(inv -> {
            CreatePurchaseEntry body = inv.getArgument(1);
            Map<Long, Long> units = body.reservations().stream()
                    .collect(Collectors.toMap(Function.identity(), id -> 1000 + id));
            return new PurchaseEntryResult(77L, units);
        });
    }

    // ---------- helpers ----------

    private Order order(LocalDateTime shippingDeadline, LocalDateTime orderDate) {
        return orderRepository.save(Order.builder().orderNumber("O").externalOrderId(uniqueExternalId())
                .marketId(1L).holderId(1L).orderDate(orderDate).shippingDeadline(shippingDeadline)
                .itemsTotal(BigDecimal.ZERO).build());
    }

    private OrderItem unit(Order order, String sku, OrderItemStatus status) {
        OrderItem fresh = OrderItem.newUnit(sku, "EXT", BigDecimal.TEN);
        return itemRepository.save(OrderItem.builder().orderId(order.getId()).sku(sku).externalItemId("EXT")
                .unitPrice(BigDecimal.TEN).status(status)
                .warehouse(status == OrderItemStatus.PROCESSING ? null : Warehouse.USA)
                .importerType(fresh.getImporterType()).build());
    }

    private RegisterPurchaseCommand cmd(int quantity, List<Long> orderItemIds) {
        return cmd(supplier.getId(), quantity, null, orderItemIds);
    }

    private RegisterPurchaseCommand cmd(Long supplierId, int quantity, String invoice, List<Long> orderItemIds) {
        return new RegisterPurchaseCommand(supplierId, SKU, "NEW", quantity, new BigDecimal("12.50"), null,
                "SUP-1", invoice, LocalDate.of(2026, 11, 15), orderItemIds);
    }

    private OrderItem reload(OrderItem item) {
        return itemRepository.findById(item.getId()).orElseThrow();
    }

    private long purchaseCount() {
        return jdbc.queryForObject("select count(*) from purchases", Long.class);
    }

    private long taskCount() {
        return jdbc.queryForObject("select count(*) from inventory_sync_tasks", Long.class);
    }

    private static List<Long> ids(List<OrderItem> items) {
        return items.stream().map(OrderItem::getId).toList();
    }

    // ---------- tests ----------

    @Test
    void register_coversMostUrgentFirst_nullDeadlineLast() {
        Order noDeadline = order(null, ORDER_DATE.minusDays(10));
        Order day10 = order(LocalDateTime.of(2026, 4, 10, 0, 0), ORDER_DATE);
        Order day5 = order(LocalDateTime.of(2026, 4, 5, 0, 0), ORDER_DATE);
        OrderItem c = unit(noDeadline, SKU, OrderItemStatus.PROCESSING);
        OrderItem a = unit(day10, SKU, OrderItemStatus.PROCESSING);
        OrderItem b = unit(day5, SKU, OrderItemStatus.PROCESSING);
        unit(day5, "OTHER-SKU", OrderItemStatus.PROCESSING);

        PurchaseView view = useCase.register("k-1", "h-1", cmd(2, null));

        Long purchaseId = view.purchase().getId();
        assertThat(purchaseId).isNotNull();
        assertThat(view.purchase().getCostCurrency()).isEqualTo("USD");
        assertThat(view.purchase().getCreatedBy()).isEqualTo("op-1");
        assertThat(view.replayed()).isFalse();
        assertThat(view.pendingSurplus()).isZero();
        assertThat(ids(view.coveredItems())).containsExactlyInAnyOrder(b.getId(), a.getId());
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(b).getPurchaseId()).isEqualTo(purchaseId);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(c).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(reload(c).getPurchaseId()).isNull();
        // reservas en orden de urgencia; sincronizado despues del commit
        verify(inventoryPort).createPurchaseEntry("purchase-" + purchaseId + "-entry",
                new CreatePurchaseEntry(purchaseId, supplier.getId(), SKU, "NEW", 2, new BigDecimal("12.50"), "USD",
                        LocalDate.of(2026, 11, 15), List.of(b.getId(), a.getId())));
        assertThat(view.inventorySync()).isEqualTo(InventorySyncState.OK);
        assertThat(view.purchase().getInventoryEntryId()).isEqualTo(77L);
        assertThat(reload(b).getInventoryUnitId()).isEqualTo(1000 + b.getId());
    }

    @Test
    void register_explicitIds_onlyThose_restSurplus() {
        Order day5 = order(LocalDateTime.of(2026, 4, 5, 0, 0), ORDER_DATE);
        Order day10 = order(LocalDateTime.of(2026, 4, 10, 0, 0), ORDER_DATE);
        OrderItem urgent = unit(day5, SKU, OrderItemStatus.PROCESSING);
        OrderItem lessUrgent = unit(day10, SKU, OrderItemStatus.PROCESSING);

        PurchaseView view = useCase.register("k-1", "h-1", cmd(3, List.of(lessUrgent.getId())));

        assertThat(ids(view.coveredItems())).containsExactly(lessUrgent.getId());
        assertThat(view.pendingSurplus()).isEqualTo(2);
        assertThat(reload(lessUrgent).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(urgent).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
    }

    @Test
    void register_explicitIdNotProcessing_409_nothingSaved() {
        Order o = order(null, ORDER_DATE);
        OrderItem ok = unit(o, SKU, OrderItemStatus.PROCESSING);
        OrderItem received = unit(o, SKU, OrderItemStatus.RECEIVED_AT_WAREHOUSE);

        assertThatThrownBy(() -> useCase.register("k-1", "h-1", cmd(2, List.of(ok.getId(), received.getId()))))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(purchaseCount()).isZero();
        assertThat(taskCount()).isZero();
        assertThat(reload(ok).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(reload(ok).getPurchaseId()).isNull();
        verify(inventoryPort, times(0)).createPurchaseEntry(anyString(), any());
    }

    @Test
    void register_explicitIds_otherSku_409_moreThanQuantity_422_duplicates_422() {
        Order o = order(null, ORDER_DATE);
        OrderItem other = unit(o, "OTHER-SKU", OrderItemStatus.PROCESSING);
        OrderItem a = unit(o, SKU, OrderItemStatus.PROCESSING);
        OrderItem b = unit(o, SKU, OrderItemStatus.PROCESSING);

        assertThatThrownBy(() -> useCase.register("k-1", "h-1", cmd(2, List.of(other.getId()))))
                .isInstanceOf(ItemSkuChangedException.class);
        assertThatThrownBy(() -> useCase.register("k-2", "h-2", cmd(1, List.of(a.getId(), b.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.register("k-3", "h-3", cmd(2, List.of(a.getId(), a.getId()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(purchaseCount()).isZero();
    }

    @Test
    void register_moreUnitsThanProcessing_surplus() {
        Order o = order(null, ORDER_DATE);
        OrderItem a = unit(o, SKU, OrderItemStatus.PROCESSING);
        OrderItem b = unit(o, SKU, OrderItemStatus.PROCESSING);
        unit(o, SKU, OrderItemStatus.PURCHASED);

        PurchaseView view = useCase.register("k-1", "h-1", cmd(5, null));

        assertThat(ids(view.coveredItems())).containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(view.pendingSurplus()).isEqualTo(3);
        assertThat(view.purchase().getQuantity()).isEqualTo(5);
    }

    @Test
    void register_sameKeySameBody_replay() {
        Order o = order(null, ORDER_DATE);
        OrderItem a = unit(o, SKU, OrderItemStatus.PROCESSING);

        PurchaseView first = useCase.register("k-1", "h-1", cmd(2, null));
        PurchaseView second = useCase.register("k-1", "h-1", cmd(2, null));

        assertThat(second.replayed()).isTrue();
        assertThat(second.purchase().getId()).isEqualTo(first.purchase().getId());
        assertThat(ids(second.coveredItems())).containsExactly(a.getId());
        assertThat(purchaseCount()).isEqualTo(1);
        assertThat(taskCount()).isEqualTo(1);
        verify(inventoryPort, times(1)).createPurchaseEntry(anyString(), any());
        assertThat(historyCount(a.getId(), HistoryEventType.PURCHASE_COVERED)).isEqualTo(1);
    }

    @Test
    void register_sameKeyOtherBody_422() {
        useCase.register("k-1", "h-1", cmd(2, null));

        assertThatThrownBy(() -> useCase.register("k-1", "h-OTHER", cmd(3, null)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(purchaseCount()).isEqualTo(1);
    }

    @Test
    void register_inactiveSupplier_409() {
        Order o = order(null, ORDER_DATE);
        OrderItem a = unit(o, SKU, OrderItemStatus.PROCESSING);
        Supplier inactive = supplierRepository.save(Supplier.create("Inactivo " + uniqueExternalId(), "CN", null, null));
        inactive.update(inactive.getName(), "CN", null, null, SupplierStatus.INACTIVE);
        supplierRepository.save(inactive);

        assertThatThrownBy(() -> useCase.register("k-1", "h-1", cmd(inactive.getId(), 1, null, null)))
                .isInstanceOf(SupplierInactiveException.class);
        assertThat(purchaseCount()).isZero();
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
    }

    @Test
    void register_inactiveCondition_422() {
        when(inventoryPort.isConditionActive("NEW")).thenReturn(false);

        assertThatThrownBy(() -> useCase.register("k-1", "h-1", cmd(1, null)))
                .isInstanceOf(IllegalArgumentException.class);

        when(inventoryPort.isConditionActive("NEW")).thenThrow(new InventoryUnavailableException("caido"));
        assertThatThrownBy(() -> useCase.register("k-2", "h-2", cmd(1, null)))
                .isInstanceOf(InventoryUnavailableException.class);
        assertThat(purchaseCount()).isZero();
    }

    @Test
    void register_invalidQuantityOrCurrency_422() {
        assertThatThrownBy(() -> useCase.register("k-1", "h-1", cmd(0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.register("k-2", "h-2", cmd(501, null)))
                .isInstanceOf(IllegalArgumentException.class);
        RegisterPurchaseCommand badCurrency = new RegisterPurchaseCommand(supplier.getId(), SKU, "NEW", 1,
                BigDecimal.ONE, "usd", "SUP-1", null, LocalDate.of(2026, 11, 15), null);
        assertThatThrownBy(() -> useCase.register("k-3", "h-3", badCurrency))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(purchaseCount()).isZero();
    }

    @Test
    void register_repeatedInvoiceSameSupplier_warns() {
        Supplier other = supplierRepository.save(Supplier.create("Otro " + uniqueExternalId(), "PE", null, null));

        PurchaseView first = useCase.register("k-1", "h-1", cmd(supplier.getId(), 1, "F-1", null));
        PurchaseView otherSupplier = useCase.register("k-2", "h-2", cmd(other.getId(), 1, "F-1", null));
        PurchaseView repeated = useCase.register("k-3", "h-3", cmd(supplier.getId(), 1, "F-1", null));

        assertThat(first.warnings()).isEmpty();
        assertThat(otherSupplier.warnings()).isEmpty();
        assertThat(repeated.warnings()).containsExactly("Factura F-1 ya registrada para este proveedor");
        assertThat(repeated.purchase().getId()).isNotNull();
    }

    @Test
    void register_inventoryDownDuringSync_purchaseSaved_syncPending() {
        Order o = order(null, ORDER_DATE);
        OrderItem a = unit(o, SKU, OrderItemStatus.PROCESSING);
        doThrow(new InventoryUnavailableException("caido"))
                .when(inventoryPort).createPurchaseEntry(anyString(), any());

        PurchaseView view = useCase.register("k-1", "h-1", cmd(2, null));

        assertThat(view.inventorySync()).isEqualTo(InventorySyncState.PENDING);
        assertThat(view.purchase().getInventoryEntryId()).isNull();
        assertThat(purchaseCount()).isEqualTo(1);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(a).getInventoryUnitId()).isNull();
        List<InventorySyncTask> tasks = taskRepository.findByPurchaseId(view.purchase().getId());
        assertThat(tasks).singleElement().satisfies(t -> {
            assertThat(t.getType()).isEqualTo(InventorySyncTaskType.CREATE_ENTRY);
            assertThat(t.getStatus()).isEqualTo(InventorySyncTaskStatus.PENDING);
            assertThat(t.getIdempotencyKey()).isEqualTo("purchase-" + view.purchase().getId() + "-entry");
        });
        assertThat(useCase.findOne(view.purchase().getId()).inventorySync()).isEqualTo(InventorySyncState.PENDING);
    }

    @Test
    void register_historyPurchaseCoveredAndStatusChanged() {
        Order o = order(null, ORDER_DATE);
        OrderItem a = unit(o, SKU, OrderItemStatus.PROCESSING);

        PurchaseView view = useCase.register("k-1", "h-1", cmd(1, null));

        List<OrderItemHistoryEntry> history = historyRepository.findByOrderItemId(a.getId());
        assertThat(history).anySatisfy(h -> {
            assertThat(h.eventType()).isEqualTo(HistoryEventType.PURCHASE_COVERED);
            assertThat(h.fromValue()).isEqualTo("PROCESSING");
            assertThat(h.toValue()).isEqualTo("purchase:" + view.purchase().getId());
            assertThat(h.performedBy()).isEqualTo("op-1");
        });
        assertThat(history).anySatisfy(h -> {
            assertThat(h.eventType()).isEqualTo(HistoryEventType.STATUS_CHANGED);
            assertThat(h.fromValue()).isEqualTo("PROCESSING");
            assertThat(h.toValue()).isEqualTo("PURCHASED");
        });
    }

    @Test
    void findOne_andSearch() {
        Order o = order(null, ORDER_DATE);
        OrderItem a = unit(o, SKU, OrderItemStatus.PROCESSING);
        PurchaseView created = useCase.register("k-1", "h-1", cmd(supplier.getId(), 3, "F-9", null));
        Long id = created.purchase().getId();

        PurchaseView found = useCase.findOne(id);

        assertThat(found.purchase().getId()).isEqualTo(id);
        assertThat(ids(found.coveredItems())).containsExactly(a.getId());
        assertThat(found.pendingSurplus()).isEqualTo(2);
        assertThat(found.arrivals()).isEmpty();
        assertThat(found.nonReceipts()).isEmpty();
        assertThat(found.inventorySync()).isEqualTo(InventorySyncState.OK);
        assertThat(found.replayed()).isFalse();
        assertThatThrownBy(() -> useCase.findOne(-1L)).isInstanceOf(PurchaseNotFoundException.class);
        assertThat(useCase.search(new PurchaseFilter(null, null, "F-9", null, null, null)))
                .extracting(p -> p.getId()).containsExactly(id);
        assertThat(useCase.search(new PurchaseFilter(null, null, "NOPE", null, null, null))).isEmpty();
    }

    @Test
    void register_idempotencyKeyBlankOrTooLong_422() {
        assertThatThrownBy(() -> useCase.register("  ", "h-1", cmd(1, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.register("k".repeat(101), "h-1", cmd(1, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(inventoryPort, times(0)).isConditionActive(anyString());
        assertThat(purchaseCount()).isZero();

        assertThat(useCase.register("k".repeat(100), "h-1", cmd(1, null)).purchase().getId()).isNotNull();
    }

    @Test
    void register_explicitIdWithPendingRequest_409_nothingSaved() {
        Order o = order(null, ORDER_DATE);
        OrderItem a = unit(o, SKU, OrderItemStatus.PROCESSING);
        OrderItem pending = unit(o, SKU, OrderItemStatus.PROCESSING);
        requestRepository.save(StatusChangeRequest.openAllocationUndo(pending.getId(), 5L, "motivo", "op-2"));

        assertThatThrownBy(() -> useCase.register("k-1", "h-1", cmd(2, List.of(a.getId(), pending.getId()))))
                .isInstanceOf(StatusChangeRequestPendingException.class);

        assertThat(purchaseCount()).isZero();
        assertThat(taskCount()).isZero();
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(reload(a).getPurchaseId()).isNull();
        assertThat(historyCount(a.getId(), HistoryEventType.PURCHASE_COVERED)).isZero();
    }
}
