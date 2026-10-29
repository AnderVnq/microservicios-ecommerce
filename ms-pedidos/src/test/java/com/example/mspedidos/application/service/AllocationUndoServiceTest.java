package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.AllocationUndoUseCase;
import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.application.port.in.InventoryAllocationUseCase;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.out.AllocationUndoRequestRepositoryPort;
import com.example.mspedidos.application.port.out.AuthorizationPort;
import com.example.mspedidos.application.port.out.InventoryPort.AllocatedUnit;
import com.example.mspedidos.domain.exception.ApprovalForbiddenException;
import com.example.mspedidos.domain.exception.AuthUnavailableException;
import com.example.mspedidos.domain.exception.InventoryReleasePendingException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.SelfApprovalNotAllowedException;
import com.example.mspedidos.domain.exception.UndoNotAllowedException;
import com.example.mspedidos.domain.exception.UndoRequestAlreadyDecidedException;
import com.example.mspedidos.domain.exception.UndoRequestPendingException;
import com.example.mspedidos.domain.model.AllocationUndoRequest;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.UndoRequestStatus;
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

class AllocationUndoServiceTest extends ServiceTestSupport {

    private static final long UNIT = 501L;
    private static final String REQUESTER = "op-1";
    private static final String ADMIN = "admin-1";
    private static final String REASON = "Se asigno la condicion equivocada";

    @MockitoBean
    AuthorizationPort authorizationPort;
    @Autowired
    AllocationUndoUseCase undo;
    @Autowired
    AllocationUndoRequestRepositoryPort undoRepository;
    @Autowired
    InventoryAllocationUseCase allocation;
    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;

    @BeforeEach
    void stubAdmin() {
        when(authorizationPort.isAllowed(ADMIN, "allocation-undo", "approve")).thenReturn(true);
    }

    /** Orden del titular 1 (DNI 111) con 2 unidades HOLDER. */
    private OrderView order() {
        stubHolder(1L, dni("111"));
        return orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(2, "10.00")));
    }

    /** Unidad cubierta desde inventario por {@code REQUESTER}. */
    private OrderItem covered(OrderItem unit, Warehouse warehouse) {
        when(inventoryPort.allocate(eq(UNIT), anyLong(), anyString()))
                .thenReturn(new AllocatedUnit(UNIT, warehouse, "NEW", new BigDecimal("50.00")));
        return allocation.allocate(unit.getOrderId(), unit.getId(), UNIT);
    }

    private AllocationUndoRequest requested(OrderItem unit) {
        actAs(REQUESTER);
        return undo.request(unit.getOrderId(), unit.getId(), REASON);
    }

    private void actAs(String actor) {
        when(actorProvider.currentActor()).thenReturn(actor);
    }

    private OrderItem reload(OrderItem item) {
        return itemRepository.findByIdAndOrderId(item.getId(), item.getOrderId()).orElseThrow();
    }

    private AllocationUndoRequest reload(AllocationUndoRequest request) {
        return undoRepository.findById(request.getId()).orElseThrow();
    }

    private List<OrderItemHistoryEntry> events(Long itemId, HistoryEventType type) {
        return historyRepository.findByOrderItemId(itemId).stream().filter(h -> h.eventType() == type).toList();
    }

    private long undoRows() {
        return jdbc.queryForObject("select count(*) from allocation_undo_requests", Long.class);
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
    void request_opensPending_writesHistory() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);

        AllocationUndoRequest request = requested(unit);

        AllocationUndoRequest stored = reload(request);
        assertThat(stored.getStatus()).isEqualTo(UndoRequestStatus.PENDING);
        assertThat(stored.getOrderItemId()).isEqualTo(unit.getId());
        assertThat(stored.getInventoryUnitId()).isEqualTo(UNIT);
        assertThat(stored.getReason()).isEqualTo(REASON);
        assertThat(stored.getRequestedBy()).isEqualTo(REQUESTER);
        assertThat(stored.getRequestedAt()).isNotNull();
        OrderItemHistoryEntry entry = events(unit.getId(), HistoryEventType.ALLOCATION_UNDO_REQUESTED).get(0);
        assertThat(entry.fromValue()).isNull();
        assertThat(entry.toValue()).isEqualTo(REASON);
        assertThat(entry.performedBy()).isEqualTo(REQUESTER);
        assertThat(undo.search(UndoRequestStatus.PENDING)).extracting(AllocationUndoRequest::getId)
                .containsExactly(request.getId());
        assertThat(undo.search(UndoRequestStatus.APPROVED)).isEmpty();
        assertThat(undo.search(null)).hasSize(1);
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
    }

    @Test
    void request_withoutReason_rejected() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);

        assertThatThrownBy(() -> undo.request(unit.getOrderId(), unit.getId(), "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> undo.request(unit.getOrderId(), unit.getId(), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(undoRows()).isZero();
        assertThat(historyCount(unit.getId(), HistoryEventType.ALLOCATION_UNDO_REQUESTED)).isZero();
    }

    @Test
    void request_twice_conflict() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        requested(unit);

        assertThatThrownBy(() -> requested(unit)).isInstanceOf(UndoRequestPendingException.class);

        assertThat(undoRows()).isEqualTo(1);
        assertThat(historyCount(unit.getId(), HistoryEventType.ALLOCATION_UNDO_REQUESTED)).isEqualTo(1);
    }

    @Test
    void request_afterUnitAdvanced_conflict() {
        OrderView view = order();
        OrderItem advanced = covered(view.items().get(0), Warehouse.USA);
        itemUseCase.changeStatus(advanced.getOrderId(), advanced.getId(), OrderItemStatus.READY_TO_SHIP, null, null);
        OrderItem notCovered = view.items().get(1);

        assertThatThrownBy(() -> requested(advanced)).isInstanceOf(UndoNotAllowedException.class);
        assertThatThrownBy(() -> requested(notCovered)).isInstanceOf(UndoNotAllowedException.class);

        assertThat(undoRows()).isZero();
    }

    @Test
    void pendingRequest_blocksStatusChangeAndCancel() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        requested(unit);
        Long orderId = unit.getOrderId();
        Long itemId = unit.getId();

        assertThatThrownBy(() -> itemUseCase.changeStatus(orderId, itemId, OrderItemStatus.READY_TO_SHIP, null, null))
                .isInstanceOf(UndoRequestPendingException.class);
        assertThatThrownBy(() -> itemUseCase.changeStatus(orderId, itemId, OrderItemStatus.CANCELLED, null, null))
                .isInstanceOf(UndoRequestPendingException.class);
        assertThatThrownBy(() -> itemUseCase.updateDetails(orderId, itemId, "SKU-2", "EXT-2", BigDecimal.ONE))
                .isInstanceOf(UndoRequestPendingException.class);
        assertThatThrownBy(() -> itemUseCase.assignImporter(orderId, itemId,
                new AssignImporterCommand(ImporterType.HOLDER, null)))
                .isInstanceOf(UndoRequestPendingException.class);

        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        assertThat(historyCount(itemId, HistoryEventType.CANCELLED)).isZero();
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void cancelOrder_withPendingUndo_conflict() {
        OrderView view = order();
        OrderItem unit = covered(view.items().get(1), Warehouse.USA);
        requested(unit);
        OrderItem other = view.items().get(0);

        assertThatThrownBy(() -> orderUseCase.cancel(view.order().getId()))
                .isInstanceOf(UndoRequestPendingException.class);

        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        assertThat(reload(other).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(historyCount(other.getId(), HistoryEventType.CANCELLED)).isZero();
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void approve_byNonAdmin_forbidden_nothingChanges() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        AllocationUndoRequest request = requested(unit);
        actAs("op-2");

        assertThatThrownBy(() -> undo.approve(request.getId(), "ok")).isInstanceOf(ApprovalForbiddenException.class);
        assertThatThrownBy(() -> undo.reject(request.getId(), "no")).isInstanceOf(ApprovalForbiddenException.class);

        verify(authorizationPort, times(2)).isAllowed("op-2", "allocation-undo", "approve");
        assertThat(reload(request).getStatus()).isEqualTo(UndoRequestStatus.PENDING);
        assertThat(reload(request).getDecidedBy()).isNull();
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        assertThat(historyCount(unit.getId(), HistoryEventType.ALLOCATION_UNDO_REJECTED)).isZero();
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void approve_bySameRequester_conflict() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        actAs(ADMIN);
        AllocationUndoRequest request = undo.request(unit.getOrderId(), unit.getId(), REASON);

        assertThatThrownBy(() -> undo.approve(request.getId(), "ok"))
                .isInstanceOf(SelfApprovalNotAllowedException.class);

        assertThat(reload(request).getStatus()).isEqualTo(UndoRequestStatus.PENDING);
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void approve_fromUsa_backToProcessing_keepsImporter_releases() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        AllocationUndoRequest request = requested(unit);
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

        AllocationUndoRequest approved = undo.approve(request.getId(), "corregir");

        assertThat(approved.getStatus()).isEqualTo(UndoRequestStatus.APPROVED);
        AllocationUndoRequest stored = reload(request);
        assertThat(stored.getStatus()).isEqualTo(UndoRequestStatus.APPROVED);
        assertThat(stored.getDecidedBy()).isEqualTo(ADMIN);
        assertThat(stored.getDecisionNote()).isEqualTo("corregir");
        assertThat(stored.getDecidedAt()).isNotNull();
        assertThat(undoRepository.findLatestApproved(unit.getId())).map(AllocationUndoRequest::getId)
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
        assertThat(incidentRepository.findOpen(unit.getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING)).isEmpty();
        assertThat(events(unit.getId(), HistoryEventType.IMPORTER_ASSIGNED))
                .noneMatch(h -> ImporterType.UNASSIGNED.name().equals(h.toValue()));

        // ya no hay solicitud pendiente: la unidad vuelve a poder cambiar y a cubrirse
        assertThatThrownBy(() -> undo.approve(request.getId(), "otra vez"))
                .isInstanceOf(UndoRequestAlreadyDecidedException.class);
        itemUseCase.changeStatus(unit.getOrderId(), unit.getId(), OrderItemStatus.PURCHASED, null, null);
    }

    @Test
    void approve_fromLima_unassigned_incidentOpened() {
        OrderItem unit = covered(order().items().get(0), Warehouse.LIMA);
        assertThat(reload(unit).getImporterType()).isEqualTo(ImporterType.NOT_REQUIRED);
        AllocationUndoRequest request = requested(unit);
        long quotaBefore = itemRepository.countQuotaUnits(dni("111"), YEAR, null);
        actAs(ADMIN);

        undo.approve(request.getId(), null);

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
        AllocationUndoRequest request = requested(unit);
        doThrow(new InventoryUnavailableException("caido")).when(inventoryPort).release(anyLong(), anyLong());
        actAs(ADMIN);

        AllocationUndoRequest approved = undo.approve(request.getId(), "ok");

        assertThat(approved.getStatus()).isEqualTo(UndoRequestStatus.APPROVED);
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
        AllocationUndoRequest request = requested(unit);
        actAs(ADMIN);

        assertThatThrownBy(() -> undo.reject(request.getId(), " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(reload(request).getStatus()).isEqualTo(UndoRequestStatus.PENDING);

        AllocationUndoRequest rejected = undo.reject(request.getId(), "La asignacion es correcta");

        assertThat(rejected.getStatus()).isEqualTo(UndoRequestStatus.REJECTED);
        AllocationUndoRequest stored = reload(request);
        assertThat(stored.getStatus()).isEqualTo(UndoRequestStatus.REJECTED);
        assertThat(stored.getDecidedBy()).isEqualTo(ADMIN);
        assertThat(stored.getDecisionNote()).isEqualTo("La asignacion es correcta");
        OrderItemHistoryEntry entry = events(unit.getId(), HistoryEventType.ALLOCATION_UNDO_REJECTED).get(0);
        assertThat(entry.fromValue()).isNull();
        assertThat(entry.toValue()).isEqualTo("La asignacion es correcta");
        assertThat(entry.performedBy()).isEqualTo(ADMIN);
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
        assertThat(undoRepository.findLatestApproved(unit.getId())).isEmpty();

        // sin solicitud pendiente la unidad vuelve a poder avanzar
        itemUseCase.changeStatus(unit.getOrderId(), unit.getId(), OrderItemStatus.READY_TO_SHIP, null, null);
        assertThat(reload(unit).getStatus()).isEqualTo(OrderItemStatus.READY_TO_SHIP);
    }

    @Test
    void approve_authDown_503() {
        OrderItem unit = covered(order().items().get(0), Warehouse.USA);
        AllocationUndoRequest request = requested(unit);
        actAs(ADMIN);
        when(authorizationPort.isAllowed(ADMIN, "allocation-undo", "approve"))
                .thenThrow(new AuthUnavailableException("ms_auth caido"));

        assertThatThrownBy(() -> undo.approve(request.getId(), "ok")).isInstanceOf(AuthUnavailableException.class);
        assertThatThrownBy(() -> undo.reject(request.getId(), "no")).isInstanceOf(AuthUnavailableException.class);

        assertThat(reload(request).getStatus()).isEqualTo(UndoRequestStatus.PENDING);
        assertStillCovered(unit, OrderItemStatus.RECEIVED_AT_WAREHOUSE, Warehouse.USA);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }
}
