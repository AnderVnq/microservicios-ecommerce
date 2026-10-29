package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.domain.exception.IdempotencyKeyReusedException;
import com.example.mspedidos.domain.exception.PurchaseNotFoundException;
import com.example.mspedidos.domain.exception.PurchaseQuantityExceededException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.PurchaseArrival;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.Warehouse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PurchaseArrivalServiceTest extends PurchaseFlowTestSupport {

    @Test
    void arrival_partialInBatches_mostUrgentFirst() {
        Order noDeadline = order(null);
        Order day10 = order(day(10));
        Order day5 = order(day(5));
        OrderItem c = processing(noDeadline);
        OrderItem b = processing(day10);
        OrderItem a = processing(day5);
        Long id = buy(3, c, b, a);

        PurchaseView first = purchases.registerArrival(id, "arr-1", "h1", 1);

        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(reload(a).getWarehouse()).isEqualTo(Warehouse.USA);
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(c).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(first.purchase().getReceivedQuantity()).isEqualTo(1);
        assertThat(first.replayed()).isFalse();
        PurchaseArrival arrival1 = first.arrivals().get(0);
        assertThat(arrival1.getOrderItemIds()).containsExactly(a.getId());
        assertThat(arrival1.getSurplusQuantity()).isZero();
        assertThat(arrival1.getCreatedBy()).isEqualTo("op-1");

        List<OrderItemHistoryEntry> arrived = history(a, HistoryEventType.PURCHASE_ARRIVED);
        assertThat(arrived).hasSize(1);
        assertThat(arrived.get(0).toValue()).isEqualTo("purchase:" + id);
        assertThat(history(a, HistoryEventType.STATUS_CHANGED)).anySatisfy(h -> {
            assertThat(h.fromValue()).isEqualTo("PURCHASED");
            assertThat(h.toValue()).isEqualTo("RECEIVED_AT_WAREHOUSE");
        });

        // tarea REGISTER_ARRIVAL con su clave, sincronizada despues del commit
        List<InventorySyncTask> tasks = tasks(id, InventorySyncTaskType.REGISTER_ARRIVAL);
        assertThat(tasks).hasSize(1);
        String key1 = "purchase-" + id + "-arrival-" + arrival1.getId();
        assertThat(tasks.get(0).getIdempotencyKey()).isEqualTo(key1);
        assertThat(tasks.get(0).getPayload()).isEqualTo("{\"quantity\":1,\"orderItemIds\":[" + a.getId() + "]}");
        assertThat(tasks.get(0).getStatus()).isEqualTo(InventorySyncTaskStatus.DONE);
        verify(inventoryPort).registerPurchaseArrival(key1, id, 1, List.of(a.getId()));

        PurchaseView second = purchases.registerArrival(id, "arr-2", "h2", 2);

        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(reload(c).getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(second.purchase().getReceivedQuantity()).isEqualTo(3);
        assertThat(second.purchase().pending()).isZero();
        assertThat(second.arrivals()).hasSize(2);
        assertThat(second.arrivals().get(1).getOrderItemIds()).containsExactly(b.getId(), c.getId());
    }

    @Test
    void arrival_surplusAfterOrders() {
        OrderItem a = processing(order(day(5)));
        Long id = buy(3, a);

        PurchaseView view = purchases.registerArrival(id, "arr-1", "h1", 3);

        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        PurchaseArrival arrival = view.arrivals().get(0);
        assertThat(arrival.getQuantity()).isEqualTo(3);
        assertThat(arrival.getOrderItemIds()).containsExactly(a.getId());
        assertThat(arrival.getSurplusQuantity()).isEqualTo(2);
        assertThat(view.purchase().getReceivedQuantity()).isEqualTo(3);
        assertThat(view.pendingSurplus()).isZero();
    }

    @Test
    void arrival_sameKey_countsOnce() {
        OrderItem a = processing(order(day(5)));
        OrderItem b = processing(order(day(6)));
        Long id = buy(2, a, b);
        Long other = buy(2);

        purchases.registerArrival(id, "arr-1", "h1", 1);
        PurchaseView replay = purchases.registerArrival(id, "arr-1", "h1", 1);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.purchase().getReceivedQuantity()).isEqualTo(1);
        assertThat(replay.arrivals()).hasSize(1);
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(tasks(id, InventorySyncTaskType.REGISTER_ARRIVAL)).hasSize(1);
        verify(inventoryPort, times(1)).registerPurchaseArrival(anyString(), anyLong(), anyInt(), any());

        // misma clave con otro cuerpo, o en otra compra (las claves son globales) -> 422
        assertThatThrownBy(() -> purchases.registerArrival(id, "arr-1", "h-other", 1))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThatThrownBy(() -> purchases.registerArrival(other, "arr-1", "h1", 1))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(purchase(other).getReceivedQuantity()).isZero();

        // clave obligatoria y de 100 caracteres como maximo
        assertThatThrownBy(() -> purchases.registerArrival(id, " ", "h1", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> purchases.registerArrival(id, "k".repeat(101), "h1", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> purchases.registerArrival(999_999L, "arr-x", "h1", 1))
                .isInstanceOf(PurchaseNotFoundException.class);
    }

    @Test
    void arrival_overPending_422() {
        OrderItem a = processing(order(day(5)));
        Long id = buy(3, a);
        purchases.registerArrival(id, "arr-1", "h1", 2);

        assertThatThrownBy(() -> purchases.registerArrival(id, "arr-2", "h2", 2))
                .isInstanceOf(PurchaseQuantityExceededException.class);
        assertThatThrownBy(() -> purchases.registerArrival(id, "arr-3", "h3", 0))
                .isInstanceOf(PurchaseQuantityExceededException.class);

        assertThat(purchase(id).getReceivedQuantity()).isEqualTo(2);
        assertThat(purchaseRepository.findArrivals(id)).hasSize(1);
        assertThat(purchaseRepository.findArrivalByKey("arr-2")).isEmpty();
        assertThat(tasks(id, InventorySyncTaskType.REGISTER_ARRIVAL)).hasSize(1);
    }

    @Test
    void arrival_exceedsPendingMinusSetAside_422() {
        OrderItem a = processing(order(day(5)));
        OrderItem b = processing(order(day(6)));
        Long id = buy(2, a, b);
        // sin sobrante: la marca incluye unidades de pedido -> solicitud PENDING que aparta 1
        notReceived(id, 1, "perdido");
        assertThat(requestRepository.findPendingByPurchaseId(id)).isPresent();

        assertThatThrownBy(() -> purchases.registerArrival(id, "arr-1", "h1", 2))
                .isInstanceOf(PurchaseQuantityExceededException.class);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);

        PurchaseView view = purchases.registerArrival(id, "arr-2", "h2", 1);
        assertThat(view.purchase().getReceivedQuantity()).isEqualTo(1);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(requestRepository.findPendingByPurchaseId(id).orElseThrow().getStatus())
                .isEqualTo(StatusChangeRequestStatus.PENDING);
    }

    @Test
    void arrival_doesNotTouchQuota() {
        Order o = order(day(5));
        OrderItem a = itemRepository.save(OrderItem.builder().orderId(o.getId()).sku(SKU).externalItemId("EXT")
                .unitPrice(BigDecimal.TEN).status(OrderItemStatus.PROCESSING).importerType(ImporterType.HOLDER)
                .importerDocument(dni("111")).build());
        Long id = buy(1, a);
        long quotaBefore = itemRepository.countQuotaUnits(dni("111"), YEAR, null);

        purchases.registerArrival(id, "arr-1", "h1", 1);

        OrderItem after = reload(a);
        assertThat(after.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(after.getImporterType()).isEqualTo(ImporterType.HOLDER);
        assertThat(after.getImporterDocument()).isEqualTo(dni("111"));
        assertThat(itemRepository.countQuotaUnits(dni("111"), YEAR, null)).isEqualTo(quotaBefore);
        assertThat(history(a, HistoryEventType.IMPORTER_ASSIGNED)).isEmpty();
    }

    @Test
    void cancelPurchased_releasesReservation() {
        Order o = order(day(5));
        OrderItem a = processing(o);
        buy(1, a);
        assertThat(reload(a).getInventoryUnitId()).isEqualTo(1000 + a.getId());

        OrderItem cancelled = itemUseCase.changeStatus(o.getId(), a.getId(), OrderItemStatus.CANCELLED, null, null);

        verify(inventoryPort).release(1000 + a.getId(), a.getId());
        assertThat(cancelled.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(reload(a).isInventoryReleasePending()).isFalse();
        assertThat(history(a, HistoryEventType.INVENTORY_RELEASED)).hasSize(1);
    }

    @Test
    void cancelReceivedFromPurchase_releasesToAvailable() {
        Order o = order(day(5));
        OrderItem a = processing(o);
        Long id = buy(1, a);
        purchases.registerArrival(id, "arr-1", "h1", 1);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);

        orderUseCase.cancel(o.getId());

        verify(inventoryPort).release(1000 + a.getId(), a.getId());
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(reload(a).isInventoryReleasePending()).isFalse();
    }

    @Test
    void cancelPurchasedBeforeSync_nothingReleasedNow() {
        doThrowOnCreateEntry();
        Order o = order(day(5));
        OrderItem a = processing(o);
        buy(1, a);

        itemUseCase.changeStatus(o.getId(), a.getId(), OrderItemStatus.CANCELLED, null, null);

        verify(inventoryPort, never()).release(anyLong(), anyLong());
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(reload(a).isInventoryReleasePending()).isFalse();
    }

    private void doThrowOnCreateEntry() {
        org.mockito.Mockito.doThrow(new com.example.mspedidos.domain.exception.InventoryUnavailableException("caido"))
                .when(inventoryPort).createPurchaseEntry(anyString(), any());
    }
}
