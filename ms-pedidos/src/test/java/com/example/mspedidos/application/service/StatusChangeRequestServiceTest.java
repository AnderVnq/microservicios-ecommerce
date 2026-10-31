package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.application.port.in.InventoryAllocationUseCase;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.in.StatusChangeRequestUseCase;
import com.example.mspedidos.application.port.out.AuthorizationPort;
import com.example.mspedidos.application.port.out.InventoryPort.AllocatedUnit;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.exception.ApprovalForbiddenException;
import com.example.mspedidos.domain.exception.AuthUnavailableException;
import com.example.mspedidos.domain.exception.InventoryReleasePendingException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.SelfApprovalNotAllowedException;
import com.example.mspedidos.domain.exception.StatusChangeRequestAlreadyDecidedException;
import com.example.mspedidos.domain.exception.StatusChangeRequestPendingException;
import com.example.mspedidos.domain.exception.UndoNotAllowedException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import com.example.mspedidos.domain.model.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusChangeRequestServiceTest extends ServiceTestSupport {

    private static final long UNIT = 501L;
    private static final String REQUESTER = "op-1";
    private static final String ADMIN = "admin-1";
    private static final String MANAGER = "manager-1";
    private static final String REASON = "Se asigno la condicion equivocada";

    @MockitoBean
    AuthorizationPort authorizationPort;
    @Autowired
    StatusChangeRequestUseCase requests;
    @Autowired
    StatusChangeRequestRepositoryPort requestRepository;
    @Autowired
    InventoryAllocationUseCase allocation;
    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;

    @BeforeEach
    void stubPermissions() {
        when(authorizationPort.isAllowed(ADMIN, "status-change", "approve")).thenReturn(true);
        when(authorizationPort.isAllowed(MANAGER, "status-change", "apply-direct")).thenReturn(true);
    }

    /** Orden del titular 1 (DNI 111) con 2 unidades HOLDER. */
    private OrderView order() {
        stubHolder(1L, dni("111"));
        return orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(2, "10.00")));
    }

    /** Unidad cubierta desde inventario. */
    private OrderItem covered(OrderItem unit, Warehouse warehouse) {
        when(inventoryPort.allocate(eq(UNIT), anyLong(), anyString()))
                .thenReturn(new AllocatedUnit(UNIT, warehouse, "NEW", new BigDecimal("50.00")));
        return allocation.allocate(unit.getOrderId(), unit.getId(), UNIT);
    }

    private StatusChangeRequest requested(OrderItem unit) {
        actAs(REQUESTER);
        return requests.requestAllocationUndo(unit.getOrderId(), unit.getId(), REASON);
    }

    private void actAs(String actor) {
        when(actorProvider.currentActor()).thenReturn(actor);
    }

    private OrderItem reload(OrderItem item) {
        return itemRepository.findByIdAndOrderId(item.getId(), item.getOrderId()).orElseThrow();
    }

    private StatusChangeRequest reload(StatusChangeRequest request) {
        return requestRepository.findById(request.getId()).orElseThrow();
    }

    private List<OrderItemHistoryEntry> events(Long itemId, HistoryEventType type) {
        return historyRepository.findByOrderItemId(itemId).stream().filter(h -> h.eventType() == type).toList();
    }

    private long requestRows() {
        return jdbc.queryForObject("select count(*) from status_change_requests", Long.class);
    }

    private void assertStillCovered(OrderItem item, OrderItemStatus status, Warehouse warehouse) {
        OrderItem current = reload(item);
        assertThat(current.getStatus()).isEqualTo(status);
        assertThat(current.getWarehouse()).isEqualTo(warehouse);
        assertThat(current.getInventoryUnitId()).isEqualTo(UNIT);
        assertThat(current.isInventoryReleasePending()).isFalse();
        assertThat(historyCount(item.getId(), HistoryEventType.ALLOCATION_UNDONE)).isZero();
    }

    @Test
    void requestAllocationUndo_opensPending_writesHistory() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);

        StatusChangeRequest request = requested(unit);

        verify(authorizationPort).isAllowed(REQUESTER, "status-change", "apply-direct");
        StatusChangeRequest stored = reload(request);
        assertThat(stored.getType()).isEqualTo(StatusChangeRequestType.ALLOCATION_UNDO);
        assertThat(stored.getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
        assertThat(stored.getOrderItemId()).isEqualTo(unit.getId());
        assertThat(stored.getInventoryUnitId()).isEqualTo(UNIT);
        assertThat(stored.getReason()).isEqualTo(REASON);
        assertThat(stored.getRequestedBy()).isEqualTo(REQUESTER);
        assertThat(stored.getRequestedAt()).isNotNull();
        assertThat(stored.getDecidedBy()).isNull();
        OrderItemHistoryEntry entry = events(unit.getId(), HistoryEventType.STATUS_CHANGE_REQUESTED).get(0);
        assertThat(entry.fromValue()).isNull();
        assertThat(entry.toValue()).isEqualTo(REASON);
        assertThat(entry.performedBy()).isEqualTo(REQUESTER);
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_APPLIED_DIRECTLY)).isZero();
        assertThat(requests.search(StatusChangeRequestStatus.PENDING, null)).extracting(StatusChangeRequest::getId)
                .containsExactly(request.getId());
        assertThat(requests.search(StatusChangeRequestStatus.APPROVED, null)).isEmpty();
        assertThat(requests.search(null, null)).hasSize(1);
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void requestAllocationUndo_withoutReason_rejected() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);

        assertThatThrownBy(() -> requests.requestAllocationUndo(unit.getOrderId(), unit.getId(), "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requests.requestAllocationUndo(unit.getOrderId(), unit.getId(), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(requestRows()).isZero();
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_REQUESTED)).isZero();
    }

    @Test
    void requestAllocationUndo_twice_conflict() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        requested(unit);

        assertThatThrownBy(() -> requested(unit)).isInstanceOf(StatusChangeRequestPendingException.class);

        assertThat(requestRows()).isEqualTo(1);
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_REQUESTED)).isEqualTo(1);
    }

    @Test
    void requestAllocationUndo_afterUnitAdvanced_conflict() {
        OrderView view = order();
        OrderItem advanced = covered(view.items().get(0), Warehouse.USA);
        itemUseCase.changeStatus(advanced.getOrderId(), advanced.getId(), OrderItemStatus.READY_TO_SHIP, null, null);
        OrderItem notCovered = view.items().get(1);

        assertThatThrownBy(() -> requested(advanced)).isInstanceOf(UndoNotAllowedException.class);
        assertThatThrownBy(() -> requested(notCovered)).isInstanceOf(UndoNotAllowedException.class);

        assertThat(requestRows()).isZero();
    }

    @Test
    void requestAllocationUndo_purchaseCoveredUnit_conflict() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        jdbc.update("update order_items set purchase_id = 777 where id = ?", unit.getId());

        assertThatThrownBy(() -> requested(unit)).isInstanceOf(UndoNotAllowedException.class);
        actAs(MANAGER);
        assertThatThrownBy(() -> requests.requestAllocationUndo(unit.getOrderId(), unit.getId(), REASON))
                .isInstanceOf(UndoNotAllowedException.class);

        assertThat(requestRows()).isZero();
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_REQUESTED)).isZero();
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_APPLIED_DIRECTLY)).isZero();
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void requestAllocationUndo_byManager_appliedDirectly_releases() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        AtomicBoolean releasedOutsideTx = new AtomicBoolean();
        AtomicBoolean flagCommittedBeforeRelease = new AtomicBoolean();
        doAnswer(inv -> {
            releasedOutsideTx.set(!TransactionSynchronizationManager.isActualTransactionActive());
            OrderItem committed = reload(unit);
            flagCommittedBeforeRelease.set(committed.isInventoryReleasePending()
                    && Long.valueOf(UNIT).equals(committed.getPendingReleaseUnitId()));
            return null;
        }).when(inventoryPort).release(UNIT, unit.getId());
        actAs(MANAGER);

        StatusChangeRequest applied = requests.requestAllocationUndo(unit.getOrderId(), unit.getId(), REASON);

        assertThat(applied.getStatus()).isEqualTo(StatusChangeRequestStatus.APPLIED_DIRECTLY);
        StatusChangeRequest stored = reload(applied);
        assertThat(stored.getStatus()).isEqualTo(StatusChangeRequestStatus.APPLIED_DIRECTLY);
        assertThat(stored.getType()).isEqualTo(StatusChangeRequestType.ALLOCATION_UNDO);
        assertThat(stored.getRequestedBy()).isEqualTo(MANAGER);
        assertThat(stored.getDecidedBy()).isEqualTo(MANAGER);
        assertThat(stored.getDecidedAt()).isNotNull();
        assertThat(stored.getReason()).isEqualTo(REASON);
        assertThat(stored.getInventoryUnitId()).isEqualTo(UNIT);
        verify(authorizationPort, never()).isAllowed(anyString(), eq("status-change"), eq("approve"));

        verify(inventoryPort).release(UNIT, unit.getId());
        assertThat(releasedOutsideTx).isTrue();
        assertThat(flagCommittedBeforeRelease).isTrue();
        OrderItem item = reload(unit);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(item.getInventoryUnitId()).isNull();
        assertThat(item.getWarehouse()).isNull();
        assertThat(item.isInventoryReleasePending()).isFalse();
        assertThat(item.getImporterType()).isEqualTo(ImporterType.HOLDER);

        OrderItemHistoryEntry direct = events(unit.getId(), HistoryEventType.STATUS_CHANGE_APPLIED_DIRECTLY).get(0);
        assertThat(direct.fromValue()).isNull();
        assertThat(direct.toValue()).isEqualTo(REASON);
        assertThat(direct.performedBy()).isEqualTo(MANAGER);
        OrderItemHistoryEntry undone = events(unit.getId(), HistoryEventType.ALLOCATION_UNDONE).get(0);
        assertThat(undone.fromValue()).isEqualTo("unit:" + UNIT);
        assertThat(undone.toValue()).isEqualTo("request:" + applied.getId() + " by " + MANAGER + ": " + REASON);
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_REQUESTED)).isZero();
        assertThat(historyCount(unit.getId(), HistoryEventType.INVENTORY_RELEASED)).isEqualTo(1);
        assertThat(requests.search(StatusChangeRequestStatus.PENDING, null)).isEmpty();

        // no queda nada pendiente: se puede volver a cubrir
        assertThatThrownBy(() -> requests.approve(applied.getId(), "x"))
                .isInstanceOf(ApprovalForbiddenException.class);
        actAs(ADMIN);
        assertThatThrownBy(() -> requests.approve(applied.getId(), "x"))
                .isInstanceOf(StatusChangeRequestAlreadyDecidedException.class);
    }

    @Test
    void apply_direct_authDown_503_nothingSaved() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        when(authorizationPort.isAllowed(REQUESTER, "status-change", "apply-direct"))
                .thenThrow(new AuthUnavailableException("ms_auth caido"));

        assertThatThrownBy(() -> requested(unit)).isInstanceOf(AuthUnavailableException.class);

        assertThat(requestRows()).isZero();
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_REQUESTED)).isZero();
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_APPLIED_DIRECTLY)).isZero();
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void search_byType() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        StatusChangeRequest undo = requested(unit);
        StatusChangeRequest notReceived = requestRepository.save(
                StatusChangeRequest.openNotReceived(900L, 2, "No llego", REQUESTER));

        assertThat(requests.search(null, StatusChangeRequestType.ALLOCATION_UNDO))
                .extracting(StatusChangeRequest::getId).containsExactly(undo.getId());
        assertThat(requests.search(null, StatusChangeRequestType.PURCHASE_NOT_RECEIVED))
                .extracting(StatusChangeRequest::getId).containsExactly(notReceived.getId());
        assertThat(requests.search(StatusChangeRequestStatus.PENDING, StatusChangeRequestType.PURCHASE_NOT_RECEIVED))
                .extracting(StatusChangeRequest::getId).containsExactly(notReceived.getId());
        assertThat(requests.search(StatusChangeRequestStatus.APPROVED, StatusChangeRequestType.ALLOCATION_UNDO))
                .isEmpty();
        assertThat(requests.search(StatusChangeRequestStatus.PENDING, null)).hasSize(2);
    }

    @Test
    void pendingRequest_blocksStatusChangeAndCancel() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        requested(unit);
        Long orderId = unit.getOrderId();
        Long itemId = unit.getId();

        assertThatThrownBy(() -> itemUseCase.changeStatus(orderId, itemId, OrderItemStatus.READY_TO_SHIP, null, null))
                .isInstanceOf(StatusChangeRequestPendingException.class);
        assertThatThrownBy(() -> itemUseCase.changeStatus(orderId, itemId, OrderItemStatus.CANCELLED, null, null))
                .isInstanceOf(StatusChangeRequestPendingException.class);
        assertThatThrownBy(() -> itemUseCase.updateDetails(orderId, itemId, "SKU-2", null, "EXT-2", BigDecimal.ONE))
                .isInstanceOf(StatusChangeRequestPendingException.class);
        assertThatThrownBy(() -> itemUseCase.assignImporter(orderId, itemId,
                new AssignImporterCommand(ImporterType.HOLDER, null)))
                .isInstanceOf(StatusChangeRequestPendingException.class);

        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        assertThat(historyCount(itemId, HistoryEventType.CANCELLED)).isZero();
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void cancelOrder_withPendingRequest_conflict() {
        OrderView view = order();
        OrderItem unit = covered(view.items().get(1), Warehouse.USA);
        requested(unit);
        OrderItem other = view.items().get(0);

        assertThatThrownBy(() -> orderUseCase.cancel(view.order().getId()))
                .isInstanceOf(StatusChangeRequestPendingException.class);

        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        assertThat(reload(other).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(historyCount(other.getId(), HistoryEventType.CANCELLED)).isZero();
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void approve_withoutPermission_forbidden_nothingChanges() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        StatusChangeRequest request = requested(unit);
        actAs("op-2");

        assertThatThrownBy(() -> requests.approve(request.getId(), "ok"))
                .isInstanceOf(ApprovalForbiddenException.class);
        assertThatThrownBy(() -> requests.reject(request.getId(), "no"))
                .isInstanceOf(ApprovalForbiddenException.class);

        verify(authorizationPort, times(2)).isAllowed("op-2", "status-change", "approve");
        assertThat(reload(request).getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
        assertThat(reload(request).getDecidedBy()).isNull();
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_REJECTED)).isZero();
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void approve_bySameRequester_conflict() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        actAs(ADMIN);
        StatusChangeRequest request = requests.requestAllocationUndo(unit.getOrderId(), unit.getId(), REASON);
        assertThat(request.getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);

        assertThatThrownBy(() -> requests.approve(request.getId(), "ok"))
                .isInstanceOf(SelfApprovalNotAllowedException.class);

        assertThat(reload(request).getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void approve_fromUsa_backToProcessing_keepsImporter_releases() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        StatusChangeRequest request = requested(unit);
        long quotaBefore = itemRepository.countQuotaUnits(dni("111"), YEAR, null);
        AtomicBoolean releasedOutsideTx = new AtomicBoolean();
        AtomicBoolean flagCommittedBeforeRelease = new AtomicBoolean();
        doAnswer(inv -> {
            releasedOutsideTx.set(!TransactionSynchronizationManager.isActualTransactionActive());
            OrderItem committed = reload(unit);
            flagCommittedBeforeRelease.set(committed.isInventoryReleasePending()
                    && Long.valueOf(UNIT).equals(committed.getPendingReleaseUnitId()));
            return null;
        }).when(inventoryPort).release(UNIT, unit.getId());
        actAs(ADMIN);

        StatusChangeRequest approved = requests.approve(request.getId(), "corregir");

        assertThat(approved.getStatus()).isEqualTo(StatusChangeRequestStatus.APPROVED);
        StatusChangeRequest stored = reload(request);
        assertThat(stored.getStatus()).isEqualTo(StatusChangeRequestStatus.APPROVED);
        assertThat(stored.getDecidedBy()).isEqualTo(ADMIN);
        assertThat(stored.getDecisionNote()).isEqualTo("corregir");
        assertThat(stored.getDecidedAt()).isNotNull();
        assertThat(requestRepository.findLatestApproved(unit.getId())).map(StatusChangeRequest::getId)
                .contains(request.getId());

        verify(inventoryPort).release(UNIT, unit.getId());
        assertThat(releasedOutsideTx).isTrue();
        assertThat(flagCommittedBeforeRelease).isTrue();
        OrderItem item = reload(unit);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(item.getInventoryUnitId()).isNull();
        assertThat(item.getWarehouse()).isNull();
        assertThat(item.isInventoryReleasePending()).isFalse();
        assertThat(item.getPendingReleaseUnitId()).isNull();
        assertThat(item.getImporterType()).isEqualTo(ImporterType.HOLDER);
        assertThat(item.getImporterDocument()).isEqualTo(dni("111"));
        assertThat(item.getUnitPrice()).isEqualByComparingTo("10.00");
        assertThat(itemRepository.countQuotaUnits(dni("111"), YEAR, null)).isEqualTo(quotaBefore);

        OrderItemHistoryEntry undone = events(unit.getId(), HistoryEventType.ALLOCATION_UNDONE).get(0);
        assertThat(undone.fromValue()).isEqualTo("unit:" + UNIT);
        assertThat(undone.toValue()).isEqualTo("request:" + request.getId() + " by " + ADMIN + ": " + REASON);
        assertThat(undone.performedBy()).isEqualTo(ADMIN);
        List<OrderItemHistoryEntry> statusChanges = events(unit.getId(), HistoryEventType.STATUS_CHANGED);
        OrderItemHistoryEntry back = statusChanges.get(statusChanges.size() - 1);
        assertThat(back.fromValue()).isEqualTo("RECEIVED_AT_WAREHOUSE");
        assertThat(back.toValue()).isEqualTo("PROCESSING");
        assertThat(historyCount(unit.getId(), HistoryEventType.INVENTORY_RELEASED)).isEqualTo(1);
        assertThat(historyCount(unit.getId(), HistoryEventType.INCIDENT_OPENED)).isZero();
        assertThat(historyCount(unit.getId(), HistoryEventType.STATUS_CHANGE_APPLIED_DIRECTLY)).isZero();
        assertThat(incidentRepository.findOpen(unit.getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING)).isEmpty();
        assertThat(events(unit.getId(), HistoryEventType.IMPORTER_ASSIGNED))
                .noneMatch(h -> ImporterType.UNASSIGNED.name().equals(h.toValue()));

        // ya no hay solicitud pendiente: la unidad vuelve a poder cambiar y a cubrirse
        assertThatThrownBy(() -> requests.approve(request.getId(), "otra vez"))
                .isInstanceOf(StatusChangeRequestAlreadyDecidedException.class);
        forcePurchased(unit.getId());
    }

    @Test
    void approve_fromLima_unassigned_incidentOpened() {
        OrderItem unit = covered(order().items().get(0), Warehouse.LIMA);
        assertThat(reload(unit).getImporterType()).isEqualTo(ImporterType.NOT_REQUIRED);
        StatusChangeRequest request = requested(unit);
        long quotaBefore = itemRepository.countQuotaUnits(dni("111"), YEAR, null);
        actAs(ADMIN);

        requests.approve(request.getId(), null);

        OrderItem item = reload(unit);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(item.getWarehouse()).isNull();
        assertThat(item.getInventoryUnitId()).isNull();
        assertThat(item.getImporterType()).isEqualTo(ImporterType.UNASSIGNED);
        assertThat(item.getImporterDocument()).isNull();
        assertThat(itemRepository.countQuotaUnits(dni("111"), YEAR, null)).isEqualTo(quotaBefore);
        ItemIncident incident = incidentRepository.findOpen(unit.getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING)
                .orElseThrow();
        assertThat(incident.getOpenedBy()).isEqualTo(ADMIN);
        assertThat(incident.getDescription()).isEqualTo("Asignacion de inventario deshecha; asignar importador");
        OrderItemHistoryEntry opened = events(unit.getId(), HistoryEventType.INCIDENT_OPENED).get(0);
        assertThat(opened.toValue()).isEqualTo(IncidentType.IMPORTER_ASSIGNMENT_PENDING.name());
        assertThat(opened.performedBy()).isEqualTo(ADMIN);
        List<OrderItemHistoryEntry> importerChanges = events(unit.getId(), HistoryEventType.IMPORTER_ASSIGNED);
        OrderItemHistoryEntry unassignedAgain = importerChanges.get(importerChanges.size() - 1);
        assertThat(unassignedAgain.fromValue()).isEqualTo(ImporterType.NOT_REQUIRED.name());
        assertThat(unassignedAgain.toValue()).isEqualTo(ImporterType.UNASSIGNED.name());
        assertThat(unassignedAgain.performedBy()).isEqualTo(ADMIN);
        List<OrderItemHistoryEntry> statusChanges = events(unit.getId(), HistoryEventType.STATUS_CHANGED);
        assertThat(statusChanges.get(statusChanges.size() - 1).fromValue()).isEqualTo("RECEIVED_LIMA");
        verify(inventoryPort).release(UNIT, unit.getId());
        assertThat(reload(request).getDecisionNote()).isNull();
    }

    @Test
    void approve_inventoryDown_flagStays_newAllocationConflict() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        StatusChangeRequest request = requested(unit);
        doThrow(new InventoryUnavailableException("caido")).when(inventoryPort).release(anyLong(), anyLong());
        actAs(ADMIN);

        StatusChangeRequest approved = requests.approve(request.getId(), "ok");

        assertThat(approved.getStatus()).isEqualTo(StatusChangeRequestStatus.APPROVED);
        OrderItem item = reload(unit);
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(item.isInventoryReleasePending()).isTrue();
        assertThat(item.getPendingReleaseUnitId()).isEqualTo(UNIT);
        assertThat(historyCount(unit.getId(), HistoryEventType.INVENTORY_RELEASED)).isZero();

        assertThatThrownBy(() -> allocation.allocate(unit.getOrderId(), unit.getId(), UNIT + 1))
                .isInstanceOf(InventoryReleasePendingException.class);
        verify(inventoryPort, times(1)).allocate(anyLong(), anyLong(), anyString());
    }

    @Test
    void reject_keepsUnitAsIs_writesHistory() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        StatusChangeRequest request = requested(unit);
        actAs(ADMIN);

        assertThatThrownBy(() -> requests.reject(request.getId(), " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(reload(request).getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);

        StatusChangeRequest rejected = requests.reject(request.getId(), "La asignacion es correcta");

        assertThat(rejected.getStatus()).isEqualTo(StatusChangeRequestStatus.REJECTED);
        StatusChangeRequest stored = reload(request);
        assertThat(stored.getStatus()).isEqualTo(StatusChangeRequestStatus.REJECTED);
        assertThat(stored.getDecidedBy()).isEqualTo(ADMIN);
        assertThat(stored.getDecisionNote()).isEqualTo("La asignacion es correcta");
        OrderItemHistoryEntry entry = events(unit.getId(), HistoryEventType.STATUS_CHANGE_REJECTED).get(0);
        assertThat(entry.fromValue()).isNull();
        assertThat(entry.toValue()).isEqualTo("La asignacion es correcta");
        assertThat(entry.performedBy()).isEqualTo(ADMIN);
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
        assertThat(requestRepository.findLatestApproved(unit.getId())).isEmpty();

        // sin solicitud pendiente la unidad vuelve a poder avanzar
        itemUseCase.changeStatus(unit.getOrderId(), unit.getId(), OrderItemStatus.READY_TO_SHIP, null, null);
        assertThat(reload(unit).getStatus()).isEqualTo(OrderItemStatus.READY_TO_SHIP);
    }

    @Test
    void approve_authDown_503() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        StatusChangeRequest request = requested(unit);
        actAs(ADMIN);
        when(authorizationPort.isAllowed(ADMIN, "status-change", "approve"))
                .thenThrow(new AuthUnavailableException("ms_auth caido"));

        assertThatThrownBy(() -> requests.approve(request.getId(), "ok"))
                .isInstanceOf(AuthUnavailableException.class);
        assertThatThrownBy(() -> requests.reject(request.getId(), "no"))
                .isInstanceOf(AuthUnavailableException.class);

        assertThat(reload(request).getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void retiredAllocationUndoPermission_notConsulted() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        StatusChangeRequest request = requested(unit);
        actAs(ADMIN);

        requests.approve(request.getId(), "ok");

        verify(authorizationPort, never()).isAllowed(anyString(), eq("allocation-undo"), anyString());
    }
}
