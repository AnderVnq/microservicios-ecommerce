package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.PurchaseUseCase;
import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.application.port.in.RegisterPurchaseCommand;
import com.example.mspedidos.application.port.in.StatusChangeRequestUseCase;
import com.example.mspedidos.application.port.out.AuthorizationPort;
import com.example.mspedidos.application.port.out.InventoryPort.CreatePurchaseEntry;
import com.example.mspedidos.application.port.out.InventoryPort.PurchaseEntryResult;
import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.application.port.out.PurchaseRepositoryPort;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.application.port.out.SupplierRepositoryPort;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Base de los tests de llegadas / no recibidas: compras sincronizadas con inventario (unitId = 1000 + itemId). */
abstract class PurchaseFlowTestSupport extends ServiceTestSupport {

    static final String SKU = "SKU-X";
    static final String MANAGER = "manager-1";
    static final String ADMIN = "admin-1";

    @MockitoBean
    AuthorizationPort authorizationPort;
    @Autowired
    PurchaseUseCase purchases;
    @Autowired
    StatusChangeRequestUseCase requests;
    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;
    @Autowired
    SupplierRepositoryPort supplierRepository;
    @Autowired
    PurchaseRepositoryPort purchaseRepository;
    @Autowired
    InventorySyncTaskRepositoryPort taskRepository;
    @Autowired
    StatusChangeRequestRepositoryPort requestRepository;

    Supplier supplier;

    @BeforeEach
    void cleanPurchasesAndStub() {
        jdbc.update("delete from inventory_sync_tasks");
        jdbc.update("delete from purchase_arrivals");
        jdbc.update("delete from purchase_non_receipts");
        jdbc.update("delete from purchases");
        supplier = supplierRepository.save(Supplier.create("Prov " + uniqueExternalId(), "US", null, null));
        when(inventoryPort.isConditionActive("NEW")).thenReturn(true);
        when(inventoryPort.createPurchaseEntry(anyString(), any())).thenAnswer(inv -> {
            CreatePurchaseEntry body = inv.getArgument(1);
            Map<Long, Long> units = body.reservations().stream()
                    .collect(Collectors.toMap(Function.identity(), id -> 1000 + id));
            return new PurchaseEntryResult(77L, units);
        });
        when(authorizationPort.isAllowed(ADMIN, "status-change", "approve")).thenReturn(true);
        when(authorizationPort.isAllowed(MANAGER, "status-change", "apply-direct")).thenReturn(true);
    }

    Order order(LocalDateTime shippingDeadline) {
        return orderRepository.save(Order.builder().orderNumber("O").externalOrderId(uniqueExternalId())
                .marketId(1L).holderId(1L).orderDate(ORDER_DATE).shippingDeadline(shippingDeadline)
                .itemsTotal(BigDecimal.ZERO).build());
    }

    OrderItem processing(Order order) {
        OrderItem fresh = OrderItem.newUnit(SKU, "EXT", BigDecimal.TEN);
        return itemRepository.save(OrderItem.builder().orderId(order.getId()).sku(SKU).externalItemId("EXT")
                .unitPrice(BigDecimal.TEN).status(OrderItemStatus.PROCESSING)
                .importerType(fresh.getImporterType()).build());
    }

    static LocalDateTime day(int d) {
        return LocalDateTime.of(2026, 4, d, 0, 0);
    }

    /** Compra de {@code quantity} que cubre exactamente {@code items}; sincronizada si inventario responde. */
    /** Marca no recibidas con una {@code Idempotency-Key} nueva. */
    com.example.mspedidos.application.port.in.NotReceivedResult notReceived(Long purchaseId, int quantity,
                                                                             String reason) {
        return purchases.markNotReceived(purchaseId, "nr-" + uniqueExternalId(), "h", quantity, reason);
    }

    Long buy(int quantity, OrderItem... items) {
        List<Long> ids = items.length == 0 ? null : java.util.Arrays.stream(items).map(OrderItem::getId).toList();
        RegisterPurchaseCommand cmd = new RegisterPurchaseCommand(supplier.getId(), SKU, "NEW", quantity,
                new BigDecimal("12.50"), null, "SUP-1", null, LocalDate.of(2026, 11, 15), ids);
        PurchaseView view = purchases.register("buy-" + uniqueExternalId(), "h", cmd);
        return view.purchase().getId();
    }

    OrderItem reload(OrderItem item) {
        return itemRepository.findById(item.getId()).orElseThrow();
    }

    Purchase purchase(Long id) {
        return purchaseRepository.findById(id).orElseThrow();
    }

    List<InventorySyncTask> tasks(Long purchaseId, InventorySyncTaskType type) {
        return taskRepository.findByPurchaseId(purchaseId).stream().filter(t -> t.getType() == type).toList();
    }

    List<OrderItemHistoryEntry> history(OrderItem item, HistoryEventType type) {
        return historyRepository.findByOrderItemId(item.getId()).stream().filter(h -> h.eventType() == type)
                .toList();
    }
}
