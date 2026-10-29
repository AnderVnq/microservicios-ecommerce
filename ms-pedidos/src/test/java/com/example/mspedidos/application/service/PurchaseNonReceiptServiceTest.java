package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.NotReceivedResult;
import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.domain.exception.IdempotencyKeyReusedException;
import com.example.mspedidos.domain.exception.InventorySyncPendingException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.NotEnoughPendingUnitsException;
import com.example.mspedidos.domain.exception.PurchaseQuantityExceededException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.PurchaseNonReceipt;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PurchaseNonReceiptServiceTest extends PurchaseFlowTestSupport {

    private long requestCount() {
        return jdbc.queryForObject("select count(*) from status_change_requests", Long.class);
    }

    private StatusChangeRequest pendingRequest(Long purchaseId) {
        return requestRepository.findPendingByPurchaseId(purchaseId).orElseThrow();
    }

    private static void assertReturned(NotReceivedResult result, Long id, StatusChangeRequestStatus status,
                                       int quantity) {
        StatusChangeRequest r = result.request();
        assertThat(r).isNotNull();
        assertThat(r.getId()).isEqualTo(id);
        assertThat(r.getStatus()).isEqualTo(status);
        assertThat(r.getType()).isEqualTo(StatusChangeRequestType.PURCHASE_NOT_RECEIVED);
        assertThat(r.getQuantity()).isEqualTo(quantity);
    }

    private void as(String actor) {
        when(actorProvider.currentActor()).thenReturn(actor);
    }

    @Test
    void notReceived_surplusOnly_appliedDirectly_noRequest() {
        OrderItem a = processing(order(day(5)));
        Long id = buy(3, a);

        NotReceivedResult result = notReceived(id, 2, "perdido");
        PurchaseView view = result.view();

        assertThat(result.request()).isNull();
        assertThat(requestCount()).isZero();
        verifyNoInteractions(authorizationPort);
        assertThat(view.purchase().getNotReceivedQuantity()).isEqualTo(2);
        assertThat(view.pendingSurplus()).isZero();
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        PurchaseNonReceipt nr = view.nonReceipts().get(0);
        assertThat(nr.getQuantity()).isEqualTo(2);
        assertThat(nr.getSurplusQuantity()).isEqualTo(2);
        assertThat(nr.getOrderItemIds()).isEmpty();
        assertThat(nr.getStatusChangeRequestId()).isNull();
        assertThat(nr.getReason()).isEqualTo("perdido");
        assertThat(nr.getAppliedBy()).isEqualTo("op-1");
        String key = "purchase-" + id + "-not-received-" + nr.getId();
        List<InventorySyncTask> tasks = tasks(id, InventorySyncTaskType.REGISTER_NOT_RECEIVED);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getIdempotencyKey()).isEqualTo(key);
        assertThat(tasks.get(0).getPayload())
                .isEqualTo("{\"quantity\":2,\"orderItemIds\":[],\"reason\":\"perdido\"}");
        verify(inventoryPort).registerPurchaseNotReceived(key, id, 2, List.of(), "perdido");

        // motivo obligatorio, cantidad >= 1, no mas que lo pendiente
        assertThatThrownBy(() -> notReceived(id, 1, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notReceived(id, 0, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notReceived(id, 2, "x"))
                .isInstanceOf(PurchaseQuantityExceededException.class);
    }

    @Test
    void notReceived_withOrders_createsPendingRequest_unitsUntouched() {
        OrderItem a = processing(order(day(5)));
        OrderItem b = processing(order(day(6)));
        Long id = buy(2, a, b);

        NotReceivedResult result = notReceived(id, 1, "perdido");
        PurchaseView view = result.view();

        StatusChangeRequest req = pendingRequest(id);
        assertReturned(result, req.getId(), StatusChangeRequestStatus.PENDING, 1);
        assertThat(req.getType()).isEqualTo(StatusChangeRequestType.PURCHASE_NOT_RECEIVED);
        assertThat(req.getQuantity()).isEqualTo(1);
        assertThat(req.getReason()).isEqualTo("perdido");
        assertThat(req.getRequestedBy()).isEqualTo("op-1");
        assertThat(req.getOrderItemId()).isNull();
        assertThat(view.purchase().getNotReceivedQuantity()).isZero();
        assertThat(view.nonReceipts()).isEmpty();
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(history(b, HistoryEventType.PURCHASE_NOT_RECEIVED)).isEmpty();
        assertThat(tasks(id, InventorySyncTaskType.REGISTER_NOT_RECEIVED)).isEmpty();

        // lo apartado por la solicitud no se puede volver a marcar
        assertThatThrownBy(() -> notReceived(id, 2, "otra"))
                .isInstanceOf(PurchaseQuantityExceededException.class);
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void notReceived_approved_leastUrgentBackToProcessing_history() {
        OrderItem urgent = processing(order(day(5)));
        OrderItem lessUrgent = processing(order(day(10)));
        OrderItem leastUrgent = processing(order(null));
        Long id = buy(4, urgent, lessUrgent, leastUrgent);
        StatusChangeRequest req = notReceived(id, 2, "perdido").request();
        assertReturned(new NotReceivedResult(null, req), pendingRequest(id).getId(),
                StatusChangeRequestStatus.PENDING, 2);

        as(ADMIN);
        StatusChangeRequest approved = requests.approve(req.getId(), "ok");

        assertThat(approved.getStatus()).isEqualTo(StatusChangeRequestStatus.APPROVED);
        // primero el sobrante (1), luego la unidad menos urgente
        OrderItem reverted = reload(leastUrgent);
        assertThat(reverted.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(reverted.getPurchaseId()).isNull();
        assertThat(reverted.getInventoryUnitId()).isNull();
        assertThat(reload(urgent).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(lessUrgent).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);

        List<OrderItemHistoryEntry> h = history(leastUrgent, HistoryEventType.PURCHASE_NOT_RECEIVED);
        assertThat(h).hasSize(1);
        assertThat(h.get(0).fromValue()).isEqualTo("purchase:" + id);
        assertThat(h.get(0).toValue()).isEqualTo("request:" + req.getId() + " by " + ADMIN + ": perdido");
        assertThat(h.get(0).performedBy()).isEqualTo(ADMIN);
        assertThat(history(leastUrgent, HistoryEventType.STATUS_CHANGED)).anySatisfy(s -> {
            assertThat(s.fromValue()).isEqualTo("PURCHASED");
            assertThat(s.toValue()).isEqualTo("PROCESSING");
        });

        PurchaseView view = purchases.findOne(id);
        assertThat(view.purchase().getNotReceivedQuantity()).isEqualTo(2);
        assertThat(view.pendingSurplus()).isZero();
        PurchaseNonReceipt nr = view.nonReceipts().get(0);
        assertThat(nr.getOrderItemIds()).containsExactly(leastUrgent.getId());
        assertThat(nr.getSurplusQuantity()).isEqualTo(1);
        assertThat(nr.getStatusChangeRequestId()).isEqualTo(req.getId());
        assertThat(nr.getAppliedBy()).isEqualTo(ADMIN);
        String key = "purchase-" + id + "-not-received-" + nr.getId();
        verify(inventoryPort).registerPurchaseNotReceived(key, id, 2, List.of(leastUrgent.getId()), "perdido");
        assertThat(tasks(id, InventorySyncTaskType.REGISTER_NOT_RECEIVED)).singleElement()
                .satisfies(t -> assertThat(t.getIdempotencyKey()).isEqualTo(key));
    }

    @Test
    void notReceived_approveWithAnotherPending_setAsideExcludesOnlyItself() {
        OrderItem a = processing(order(day(5)));
        OrderItem b = processing(order(day(10)));
        OrderItem c = processing(order(day(15)));
        Long id = buy(3, a, b, c);
        StatusChangeRequest first = notReceived(id, 1, "uno").request();
        StatusChangeRequest second = notReceived(id, 2, "dos").request();
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
        assertThat(second.getQuantity()).isEqualTo(2);

        as(ADMIN);
        requests.approve(second.getId(), null);

        assertThat(reload(c).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(purchase(id).getNotReceivedQuantity()).isEqualTo(2);

        requests.approve(first.getId(), null);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(purchase(id).pending()).isZero();
    }

    @Test
    void notReceived_byManager_appliedDirectly() {
        OrderItem a = processing(order(day(5)));
        OrderItem b = processing(order(day(10)));
        Long id = buy(2, a, b);

        as(MANAGER);
        NotReceivedResult result = notReceived(id, 1, "perdido");
        PurchaseView view = result.view();

        StatusChangeRequest req = requestRepository.search(null, StatusChangeRequestType.PURCHASE_NOT_RECEIVED)
                .get(0);
        assertReturned(result, req.getId(), StatusChangeRequestStatus.APPLIED_DIRECTLY, 1);
        assertThat(req.getStatus()).isEqualTo(StatusChangeRequestStatus.APPLIED_DIRECTLY);
        assertThat(req.getDecidedBy()).isEqualTo(MANAGER);
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        List<OrderItemHistoryEntry> h = history(b, HistoryEventType.PURCHASE_NOT_RECEIVED);
        assertThat(h).singleElement().satisfies(e -> {
            assertThat(e.toValue()).isEqualTo("direct by " + MANAGER + ": perdido");
            assertThat(e.performedBy()).isEqualTo(MANAGER);
        });
        assertThat(view.purchase().getNotReceivedQuantity()).isEqualTo(1);
        assertThat(view.nonReceipts()).singleElement()
                .satisfies(nr -> assertThat(nr.getStatusChangeRequestId()).isEqualTo(req.getId()));
        verify(inventoryPort).registerPurchaseNotReceived(anyString(), any(), anyInt(), any(), any());
    }

    @Test
    void notReceived_beforeSync_409() {
        doThrow(new InventoryUnavailableException("x")).when(inventoryPort).createPurchaseEntry(anyString(), any());
        OrderItem a = processing(order(day(5)));
        Long withOrders = buy(1, a);
        Long surplusOnly = buy(2);

        assertThatThrownBy(() -> notReceived(withOrders, 1, "perdido"))
                .isInstanceOf(InventorySyncPendingException.class);
        assertThatThrownBy(() -> notReceived(surplusOnly, 1, "perdido"))
                .isInstanceOf(InventorySyncPendingException.class);

        assertThat(requestCount()).isZero();
        assertThat(purchase(withOrders).getNotReceivedQuantity()).isZero();
        assertThat(purchase(surplusOnly).getNotReceivedQuantity()).isZero();
        verifyNoInteractions(authorizationPort);
    }

    @Test
    void notReceived_approvedAfterArrivalsConsumedPending_409() {
        OrderItem a = processing(order(day(5)));
        OrderItem b = processing(order(day(10)));
        Long id = buy(2, a, b);
        notReceived(id, 1, "perdido");
        StatusChangeRequest req = pendingRequest(id);
        // lo apartado impide que una llegada lo consuma por la API; se simula que ya no queda nada pendiente
        jdbc.update("update purchases set received_quantity = quantity where id = ?", id);

        as(ADMIN);
        assertThatThrownBy(() -> requests.approve(req.getId(), null))
                .isInstanceOf(NotEnoughPendingUnitsException.class);

        assertThat(pendingRequest(id).getId()).isEqualTo(req.getId());
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(purchase(id).getNotReceivedQuantity()).isZero();
        verify(inventoryPort, never()).registerPurchaseNotReceived(anyString(), anyLong(), anyInt(), any(), any());

        StatusChangeRequest rejected = requests.reject(req.getId(), "ya llego");
        assertThat(rejected.getStatus()).isEqualTo(StatusChangeRequestStatus.REJECTED);
    }

    @Test
    void cancelWhileNotReceivedPending_allowed() {
        Order o1 = order(day(5));
        Order o2 = order(day(10));
        OrderItem a = processing(o1);
        OrderItem b = processing(o2);
        Long id = buy(2, a, b);
        notReceived(id, 1, "perdido");
        StatusChangeRequest req = pendingRequest(id);

        itemUseCase.changeStatus(o1.getId(), a.getId(), OrderItemStatus.CANCELLED, null, null);
        orderUseCase.cancel(o2.getId());

        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(reload(b).getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        verify(inventoryPort).release(1000 + a.getId(), a.getId());
        verify(inventoryPort).release(1000 + b.getId(), b.getId());
        // las unidades fisicas pasan a sobrante y siguen pendientes
        assertThat(purchases.findOne(id).pendingSurplus()).isEqualTo(2);

        as(ADMIN);
        requests.approve(req.getId(), null);
        PurchaseView view = purchases.findOne(id);
        assertThat(view.purchase().getNotReceivedQuantity()).isEqualTo(1);
        assertThat(view.nonReceipts()).singleElement().satisfies(nr -> {
            assertThat(nr.getSurplusQuantity()).isEqualTo(1);
            assertThat(nr.getOrderItemIds()).isEmpty();
        });
    }

    // ---------------------------------------------------------------- idempotencia (anti-objetivo 4)

    private long nonReceiptCount(Long purchaseId) {
        return jdbc.queryForObject("select count(*) from purchase_non_receipts where purchase_id = ?", Long.class,
                purchaseId);
    }

    @Test
    void notReceived_surplusOnly_sameKeyReplays_otherHashOrPurchase422() {
        Long id = buy(3);
        Long other = buy(1);

        NotReceivedResult first = purchases.markNotReceived(id, "nr-k1", "h1", 2, "perdido");
        NotReceivedResult again = purchases.markNotReceived(id, "nr-k1", "h1", 2, "perdido");

        assertThat(first.request()).isNull();
        assertThat(again.request()).isNull();
        assertThat(purchase(id).getNotReceivedQuantity()).isEqualTo(2);
        assertThat(nonReceiptCount(id)).isEqualTo(1);
        assertThat(tasks(id, InventorySyncTaskType.REGISTER_NOT_RECEIVED)).hasSize(1);
        PurchaseNonReceipt nr = purchases.findOne(id).nonReceipts().get(0);
        assertThat(nr.getIdempotencyKey()).isEqualTo("nr-k1");
        assertThat(nr.getRequestHash()).isEqualTo("h1");

        assertThatThrownBy(() -> purchases.markNotReceived(id, "nr-k1", "h2", 1, "otro"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThatThrownBy(() -> purchases.markNotReceived(other, "nr-k1", "h1", 1, "perdido"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(purchase(id).getNotReceivedQuantity()).isEqualTo(2);
        assertThat(purchase(other).getNotReceivedQuantity()).isZero();
    }

    @Test
    void notReceived_request_sameKeyReturnsSameRequest_evenAfterApproval() {
        OrderItem a = processing(order(day(5)));
        OrderItem b = processing(order(day(6)));
        Long id = buy(2, a, b);

        NotReceivedResult first = purchases.markNotReceived(id, "nr-k2", "h1", 1, "perdido");
        NotReceivedResult again = purchases.markNotReceived(id, "nr-k2", "h1", 1, "perdido");

        assertThat(first.request().getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
        assertThat(again.request().getId()).isEqualTo(first.request().getId());
        assertThat(requestCount()).isEqualTo(1);
        assertThat(first.request().getIdempotencyKey()).isEqualTo("nr-k2");
        assertThatThrownBy(() -> purchases.markNotReceived(id, "nr-k2", "h2", 1, "perdido"))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        as(ADMIN);
        requests.approve(first.request().getId(), null);
        as("op-1");
        NotReceivedResult afterApproval = purchases.markNotReceived(id, "nr-k2", "h1", 1, "perdido");

        assertThat(afterApproval.request().getId()).isEqualTo(first.request().getId());
        assertThat(afterApproval.request().getStatus()).isEqualTo(StatusChangeRequestStatus.APPROVED);
        assertThat(purchase(id).getNotReceivedQuantity()).isEqualTo(1);
        assertThat(nonReceiptCount(id)).isEqualTo(1);
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void notReceived_appliedDirectly_sameKeyNotAppliedTwice() {
        OrderItem a = processing(order(day(5)));
        OrderItem b = processing(order(day(10)));
        Long id = buy(2, a, b);
        as(MANAGER);

        NotReceivedResult first = purchases.markNotReceived(id, "nr-k3", "h1", 1, "perdido");
        NotReceivedResult again = purchases.markNotReceived(id, "nr-k3", "h1", 1, "perdido");

        assertThat(first.request().getStatus()).isEqualTo(StatusChangeRequestStatus.APPLIED_DIRECTLY);
        assertThat(again.request().getId()).isEqualTo(first.request().getId());
        assertThat(purchase(id).getNotReceivedQuantity()).isEqualTo(1);
        assertThat(reload(a).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(nonReceiptCount(id)).isEqualTo(1);
        assertThat(tasks(id, InventorySyncTaskType.REGISTER_NOT_RECEIVED)).hasSize(1);
    }

    /** Con el lock de la compra, un borrador cuya clave ya se uso (en cualquiera de las dos tablas) no se crea. */
    @Test
    void submit_draftWithUsedKey_detectedUnderLock() {
        OrderItem a = processing(order(day(5)));
        Long id = buy(2, a);
        purchases.markNotReceived(id, "nr-k4", "h1", 1, "perdido");

        assertThatThrownBy(() -> requests.submit(StatusChangeRequest.openNotReceived(id, 1, "perdido", "op-1",
                "nr-k4", "h1"))).isInstanceOf(NotReceivedApplier.KeyAlreadyUsedException.class);
        assertThat(requestCount()).isZero();
        assertThat(purchase(id).getNotReceivedQuantity()).isEqualTo(1);
    }

    @Test
    void notReceived_keyRequired() {
        Long id = buy(1);
        assertThatThrownBy(() -> purchases.markNotReceived(id, " ", "h", 1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> purchases.markNotReceived(id, "k".repeat(101), "h", 1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
