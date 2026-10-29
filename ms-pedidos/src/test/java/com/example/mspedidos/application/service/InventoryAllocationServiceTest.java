package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.InventoryAllocationUseCase;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.out.InventoryPort.AllocatedUnit;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.domain.exception.InvalidStatusTransitionException;
import com.example.mspedidos.domain.exception.InventoryAllocationConflictException;
import com.example.mspedidos.domain.exception.InventoryReleasePendingException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.InventoryUnitNotFoundException;
import com.example.mspedidos.domain.exception.ItemSkuChangedException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Warehouse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryAllocationServiceTest extends ServiceTestSupport {

    private static final long UNIT = 501L;

    @MockitoSpyBean
    OrderItemRepositoryPort spiedItems;
    @Autowired
    InventoryAllocationUseCase allocation;
    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;

    /** Orden del titular 1 con 4 unidades: 3 HOLDER (DNI 111) y la ultima UNASSIGNED con incidencia abierta. */
    private OrderView orderWithFourUnits() {
        stubHolder(1L, dni("111"));
        return orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(4, "10.00")));
    }

    private void stubAllocate(long unitId, Warehouse warehouse) {
        stubAllocate(unitId, warehouse, "NEW");
    }

    private void stubAllocate(long unitId, Warehouse warehouse, String conditionCode) {
        when(inventoryPort.allocate(eq(unitId), anyLong(), anyString()))
                .thenReturn(new AllocatedUnit(unitId, warehouse, conditionCode, new BigDecimal("50.00")));
    }

    private OrderItem reload(OrderItem item) {
        return itemRepository.findByIdAndOrderId(item.getId(), item.getOrderId()).orElseThrow();
    }

    private List<OrderItemHistoryEntry> events(Long itemId, HistoryEventType type) {
        return historyRepository.findByOrderItemId(itemId).stream().filter(h -> h.eventType() == type).toList();
    }

    private OrderItemHistoryEntry lastEvent(Long itemId, HistoryEventType type) {
        List<OrderItemHistoryEntry> found = events(itemId, type);
        return found.get(found.size() - 1);
    }

    private void assertUntouched(OrderItem item) {
        OrderItem current = reload(item);
        assertThat(current.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(current.getInventoryUnitId()).isNull();
        assertThat(current.getWarehouse()).isNull();
        assertThat(current.getImporterType()).isEqualTo(item.getImporterType());
        assertThat(historyCount(item.getId(), HistoryEventType.INVENTORY_ALLOCATED)).isZero();
    }

    @Test
    void allocate_usa_receivedAtWarehouse_quotaUntouched() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);
        stubAllocate(UNIT, Warehouse.USA);
        long quotaBefore = itemRepository.countQuotaUnits(dni("111"), YEAR, null);
        long importerEventsBefore = historyCount(unit.getId(), HistoryEventType.IMPORTER_ASSIGNED);

        OrderItem result = allocation.allocate(view.order().getId(), unit.getId(), UNIT);

        verify(inventoryPort).allocate(UNIT, unit.getId(), "SKU-1");
        OrderItem stored = reload(unit);
        assertThat(result.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(stored.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);
        assertThat(stored.getWarehouse()).isEqualTo(Warehouse.USA);
        assertThat(stored.getInventoryUnitId()).isEqualTo(UNIT);
        assertThat(stored.getImporterType()).isEqualTo(ImporterType.HOLDER);
        assertThat(stored.getImporterDocument()).isEqualTo(dni("111"));
        assertThat(itemRepository.countQuotaUnits(dni("111"), YEAR, null)).isEqualTo(quotaBefore);

        OrderItemHistoryEntry allocated = events(unit.getId(), HistoryEventType.INVENTORY_ALLOCATED).get(0);
        assertThat(allocated.fromValue()).isNull();
        assertThat(allocated.toValue()).isEqualTo("unit:" + UNIT + "@USA/NEW");
        assertThat(allocated.performedBy()).isEqualTo("op-1");
        OrderItemHistoryEntry status = events(unit.getId(), HistoryEventType.STATUS_CHANGED).get(0);
        assertThat(status.fromValue()).isEqualTo("PROCESSING");
        assertThat(status.toValue()).isEqualTo("RECEIVED_AT_WAREHOUSE");
        assertThat(historyCount(unit.getId(), HistoryEventType.IMPORTER_ASSIGNED)).isEqualTo(importerEventsBefore);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void allocate_lima_receivedLima_notRequired_freesQuota_resolvesIncident() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem holderUnit = view.items().get(0);
        OrderItem unassigned = view.items().get(3);
        ItemIncident incident = incidentRepository.findOpen(unassigned.getId(),
                IncidentType.IMPORTER_ASSIGNMENT_PENDING).orElseThrow();
        stubAllocate(UNIT, Warehouse.LIMA, "OPEN_BOX");
        stubAllocate(UNIT + 1, Warehouse.LIMA);
        assertThat(itemRepository.countQuotaUnits(dni("111"), YEAR, null)).isEqualTo(3);

        allocation.allocate(orderId, holderUnit.getId(), UNIT);

        OrderItem stored = reload(holderUnit);
        assertThat(stored.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_LIMA);
        assertThat(stored.getWarehouse()).isEqualTo(Warehouse.LIMA);
        assertThat(stored.getImporterType()).isEqualTo(ImporterType.NOT_REQUIRED);
        assertThat(stored.getImporterDocument()).isNull();
        assertThat(itemRepository.countQuotaUnits(dni("111"), YEAR, null)).isEqualTo(2);
        OrderItemHistoryEntry importer = lastEvent(holderUnit.getId(), HistoryEventType.IMPORTER_ASSIGNED);
        assertThat(importer.fromValue()).isEqualTo("HOLDER:111");
        assertThat(importer.toValue()).isEqualTo("NOT_REQUIRED");
        assertThat(events(holderUnit.getId(), HistoryEventType.INVENTORY_ALLOCATED).get(0).toValue())
                .isEqualTo("unit:" + UNIT + "@LIMA/OPEN_BOX");
        assertThat(events(holderUnit.getId(), HistoryEventType.STATUS_CHANGED).get(0).toValue())
                .isEqualTo("RECEIVED_LIMA");
        assertThat(historyCount(holderUnit.getId(), HistoryEventType.INCIDENT_RESOLVED)).isZero();

        allocation.allocate(orderId, unassigned.getId(), UNIT + 1);

        ItemIncident resolved = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedBy()).isEqualTo("op-1");
        assertThat(events(unassigned.getId(), HistoryEventType.INCIDENT_RESOLVED).get(0).fromValue())
                .isEqualTo("IMPORTER_ASSIGNMENT_PENDING");
        OrderItemHistoryEntry fromUnassigned = lastEvent(unassigned.getId(), HistoryEventType.IMPORTER_ASSIGNED);
        assertThat(fromUnassigned.fromValue()).isEqualTo("UNASSIGNED");
        assertThat(fromUnassigned.toValue()).isEqualTo("NOT_REQUIRED");
        assertThat(reload(unassigned).getImporterType()).isEqualTo(ImporterType.NOT_REQUIRED);
    }

    @Test
    void allocate_retrySameUnit_returnsItemWithoutSecondCall() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        stubAllocate(UNIT, Warehouse.USA);
        allocation.allocate(orderId, unit.getId(), UNIT);

        OrderItem retry = allocation.allocate(orderId, unit.getId(), UNIT);
        assertThat(retry.getInventoryUnitId()).isEqualTo(UNIT);
        assertThat(retry.getStatus()).isEqualTo(OrderItemStatus.RECEIVED_AT_WAREHOUSE);

        // aunque la unidad ya haya avanzado, el reintento con la misma unidad responde la unidad
        itemUseCase.changeStatus(orderId, unit.getId(), OrderItemStatus.READY_TO_SHIP, null, null);
        OrderItem lateRetry = allocation.allocate(orderId, unit.getId(), UNIT);
        assertThat(lateRetry.getStatus()).isEqualTo(OrderItemStatus.READY_TO_SHIP);

        verify(inventoryPort, times(1)).allocate(anyLong(), anyLong(), anyString());
        assertThat(historyCount(unit.getId(), HistoryEventType.INVENTORY_ALLOCATED)).isEqualTo(1);
    }

    @Test
    void allocate_notProcessing_conflict_noRemoteCall() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem purchased = view.items().get(0);
        forcePurchased(purchased.getId());

        assertThat(catchThrowable(() -> allocation.allocate(orderId, purchased.getId(), UNIT)))
                .isInstanceOf(InvalidStatusTransitionException.class);

        // cubierta por otra unidad de inventario
        OrderItem covered = view.items().get(1);
        jdbc.update("update order_items set inventory_unit_id = ? where id = ?", 777L, covered.getId());
        assertThat(catchThrowable(() -> allocation.allocate(orderId, covered.getId(), UNIT)))
                .isInstanceOf(InvalidStatusTransitionException.class);

        // liberacion pendiente (tras deshacer una asignacion)
        OrderItem pending = view.items().get(2);
        jdbc.update("update order_items set inventory_release_pending = true, pending_release_unit_id = ? "
                + "where id = ?", 778L, pending.getId());
        assertThat(catchThrowable(() -> allocation.allocate(orderId, pending.getId(), UNIT)))
                .isInstanceOf(InventoryReleasePendingException.class);

        verifyNoInteractions(inventoryPort);
        assertThat(reload(purchased).getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(reload(covered).getInventoryUnitId()).isEqualTo(777L);
        assertThat(reload(pending).getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
    }

    @Test
    void allocate_inventoryDown_503_noChange() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);
        when(inventoryPort.allocate(anyLong(), anyLong(), anyString()))
                .thenThrow(new InventoryUnavailableException("caido"));

        assertThat(catchThrowable(() -> allocation.allocate(view.order().getId(), unit.getId(), UNIT)))
                .isInstanceOf(InventoryUnavailableException.class);

        assertUntouched(unit);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void allocate_inventoryConflict_409_noChange() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);
        when(inventoryPort.allocate(anyLong(), anyLong(), anyString()))
                .thenThrow(new InventoryAllocationConflictException("ya asignada"));

        assertThat(catchThrowable(() -> allocation.allocate(view.order().getId(), unit.getId(), UNIT)))
                .isInstanceOf(InventoryAllocationConflictException.class);

        assertUntouched(unit);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
    }

    @Test
    void allocate_localTxFails_releasesInInventory() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);
        stubAllocate(UNIT, Warehouse.USA);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("bd caida");
        doThrow(failure).when(spiedItems).save(argThat(i -> unit.getId().equals(i.getId())));

        Throwable thrown = catchThrowable(() -> allocation.allocate(view.order().getId(), unit.getId(), UNIT));

        assertThat(thrown).isSameAs(failure);
        verify(inventoryPort, times(1)).release(UNIT, unit.getId());
        assertUntouched(unit);
    }

    @Test
    void allocate_localTxFails_compensationFails_rethrowsOriginal() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);
        stubAllocate(UNIT, Warehouse.USA);
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("bd caida");
        doThrow(failure).when(spiedItems).save(argThat(i -> unit.getId().equals(i.getId())));
        doThrow(new InventoryUnavailableException("caido")).when(inventoryPort).release(anyLong(), anyLong());

        Throwable thrown = catchThrowable(() -> allocation.allocate(view.order().getId(), unit.getId(), UNIT));

        assertThat(thrown).isSameAs(failure);
        verify(inventoryPort, times(1)).release(UNIT, unit.getId());
        assertUntouched(unit);
    }

    @Test
    void allocate_statusChangedBeforeLocalTx_compensatesAndLeavesItem() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        // otro operador avanza la unidad mientras inventario asigna
        when(inventoryPort.allocate(anyLong(), anyLong(), anyString())).thenAnswer(inv -> {
            forcePurchased(unit.getId());
            return new AllocatedUnit(UNIT, Warehouse.USA, "NEW", BigDecimal.TEN);
        });

        assertThat(catchThrowable(() -> allocation.allocate(orderId, unit.getId(), UNIT)))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(inventoryPort, times(1)).release(UNIT, unit.getId());
        OrderItem current = reload(unit);
        assertThat(current.getStatus()).isEqualTo(OrderItemStatus.PURCHASED);
        assertThat(current.getInventoryUnitId()).isNull();
        assertThat(historyCount(unit.getId(), HistoryEventType.INVENTORY_ALLOCATED)).isZero();
    }

    @Test
    void allocate_releasePendingSetBeforeLocalTx_compensatesAndLeavesItem() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        // se deshace una asignacion anterior mientras inventario asigna: la unidad queda con liberacion pendiente
        when(inventoryPort.allocate(anyLong(), anyLong(), anyString())).thenAnswer(inv -> {
            jdbc.update("update order_items set inventory_release_pending = true, pending_release_unit_id = ? "
                    + "where id = ?", 778L, unit.getId());
            return new AllocatedUnit(UNIT, Warehouse.USA, "NEW", BigDecimal.TEN);
        });

        assertThat(catchThrowable(() -> allocation.allocate(orderId, unit.getId(), UNIT)))
                .isInstanceOf(InventoryReleasePendingException.class);

        verify(inventoryPort, times(1)).release(UNIT, unit.getId());
        verify(inventoryPort, never()).release(778L, unit.getId());
        OrderItem current = reload(unit);
        assertThat(current.getInventoryUnitId()).isNull();
        assertThat(current.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(current.getPendingReleaseUnitId()).isEqualTo(778L);
    }

    @Test
    void allocate_skuChangedBeforeLocalTx_conflictCompensatesAndLeavesItem() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        // el SKU cambia mientras inventario asigna una unidad del SKU anterior
        when(inventoryPort.allocate(anyLong(), anyLong(), anyString())).thenAnswer(inv -> {
            itemUseCase.updateDetails(orderId, unit.getId(), "SKU-2", "EXT-2", new BigDecimal("10.00"));
            return new AllocatedUnit(UNIT, Warehouse.USA, "NEW", BigDecimal.TEN);
        });

        Throwable thrown = catchThrowable(() -> allocation.allocate(orderId, unit.getId(), UNIT));

        assertThat(thrown).isInstanceOf(ItemSkuChangedException.class);
        verify(inventoryPort).allocate(UNIT, unit.getId(), "SKU-1");
        verify(inventoryPort, times(1)).release(UNIT, unit.getId());
        OrderItem current = reload(unit);
        assertThat(current.getSku()).isEqualTo("SKU-2");
        assertThat(current.getStatus()).isEqualTo(OrderItemStatus.PROCESSING);
        assertThat(current.getInventoryUnitId()).isNull();
        assertThat(historyCount(unit.getId(), HistoryEventType.INVENTORY_ALLOCATED)).isZero();
    }

    @Test
    void allocate_sameUnitCoveredConcurrently_returnsWithoutRelease() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        // un reintento concurrente (doble clic) confirma la misma asignacion mientras esta llamada espera
        when(inventoryPort.allocate(anyLong(), anyLong(), anyString())).thenAnswer(inv -> {
            jdbc.update("update order_items set inventory_unit_id = ?, status = 'RECEIVED_AT_WAREHOUSE', "
                    + "warehouse = 'USA' where id = ?", UNIT, unit.getId());
            return new AllocatedUnit(UNIT, Warehouse.USA, "NEW", BigDecimal.TEN);
        });

        OrderItem result = allocation.allocate(orderId, unit.getId(), UNIT);

        assertThat(result.getInventoryUnitId()).isEqualTo(UNIT);
        verify(inventoryPort, never()).release(anyLong(), anyLong());
        assertThat(reload(unit).getInventoryUnitId()).isEqualTo(UNIT);
    }

    @Test
    void cancelCoveredUnit_cancelsAndReleases_historyInventoryReleased() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        stubAllocate(UNIT, Warehouse.USA);
        allocation.allocate(orderId, unit.getId(), UNIT);
        AtomicBoolean releasedOutsideTx = new AtomicBoolean();
        doAnswer(inv -> {
            releasedOutsideTx.set(!TransactionSynchronizationManager.isActualTransactionActive());
            // la cancelacion ya esta confirmada cuando se llama a inventario
            assertThat(reload(unit).getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
            return null;
        }).when(inventoryPort).release(anyLong(), anyLong());

        OrderItem result = itemUseCase.changeStatus(orderId, unit.getId(), OrderItemStatus.CANCELLED, null, null);

        verify(inventoryPort, times(1)).release(UNIT, unit.getId());
        assertThat(releasedOutsideTx).isTrue();
        assertThat(result.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(result.isInventoryReleasePending()).isFalse();
        OrderItem stored = reload(unit);
        assertThat(stored.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(stored.isInventoryReleasePending()).isFalse();
        assertThat(stored.getPendingReleaseUnitId()).isNull();
        OrderItemHistoryEntry released = events(unit.getId(), HistoryEventType.INVENTORY_RELEASED).get(0);
        assertThat(released.fromValue()).isEqualTo("unit:" + UNIT);
        assertThat(released.toValue()).isNull();
        assertThat(released.performedBy()).isEqualTo("op-1");
        assertThat(historyCount(unit.getId(), HistoryEventType.CANCELLED)).isEqualTo(1);
    }

    @Test
    void cancelCoveredUnit_inventoryDown_cancelStillDone_flagSet() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        stubAllocate(UNIT, Warehouse.USA);
        allocation.allocate(orderId, unit.getId(), UNIT);
        doThrow(new InventoryUnavailableException("caido")).when(inventoryPort).release(anyLong(), anyLong());

        OrderItem result = itemUseCase.changeStatus(orderId, unit.getId(), OrderItemStatus.CANCELLED, null, null);

        verify(inventoryPort, times(1)).release(UNIT, unit.getId());
        assertThat(result.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(result.isInventoryReleasePending()).isTrue();
        OrderItem stored = reload(unit);
        assertThat(stored.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThat(stored.isInventoryReleasePending()).isTrue();
        assertThat(stored.getPendingReleaseUnitId()).isEqualTo(UNIT);
        assertThat(stored.getInventoryUnitId()).isEqualTo(UNIT);
        assertThat(historyCount(unit.getId(), HistoryEventType.INVENTORY_RELEASED)).isZero();
    }

    @Test
    void cancelCoveredUnit_inventoryConflictOnRelease_treatedAsReleased() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        stubAllocate(UNIT, Warehouse.USA);
        allocation.allocate(orderId, unit.getId(), UNIT);
        // 409: la unidad de inventario ya no esta asignada a esta unidad de pedido; reintentar no sirve
        doThrow(new InventoryAllocationConflictException("otra")).when(inventoryPort).release(anyLong(), anyLong());

        OrderItem result = itemUseCase.changeStatus(orderId, unit.getId(), OrderItemStatus.CANCELLED, null, null);

        assertThat(result.isInventoryReleasePending()).isFalse();
        OrderItem stored = reload(unit);
        assertThat(stored.isInventoryReleasePending()).isFalse();
        assertThat(stored.getPendingReleaseUnitId()).isNull();
        OrderItemHistoryEntry released = events(unit.getId(), HistoryEventType.INVENTORY_RELEASED).get(0);
        assertThat(released.fromValue()).isEqualTo("unit:" + UNIT);
        assertThat(released.toValue()).isEqualTo("conflict:INVENTORY_ALLOCATION_CONFLICT");
    }

    @Test
    void cancelCoveredUnit_inventoryUnitNotFoundOnRelease_treatedAsReleased() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        stubAllocate(UNIT, Warehouse.USA);
        allocation.allocate(orderId, unit.getId(), UNIT);
        doThrow(new InventoryUnitNotFoundException(UNIT)).when(inventoryPort).release(anyLong(), anyLong());

        itemUseCase.changeStatus(orderId, unit.getId(), OrderItemStatus.CANCELLED, null, null);

        assertThat(reload(unit).isInventoryReleasePending()).isFalse();
        assertThat(events(unit.getId(), HistoryEventType.INVENTORY_RELEASED).get(0).toValue())
                .isEqualTo("conflict:INVENTORY_UNIT_NOT_FOUND");
    }

    @Test
    void changeStatusNotCancel_onPendingItem_doesNotCallInventory() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem unit = view.items().get(0);
        jdbc.update("update order_items set inventory_release_pending = true, pending_release_unit_id = ? "
                + "where id = ?", 778L, unit.getId());

        forcePurchased(unit.getId());

        verifyNoInteractions(inventoryPort);
        assertThat(reload(unit).isInventoryReleasePending()).isTrue();
    }

    @Test
    void cancelOrder_withCoveredUnits_releasesEachAfterCommit() {
        OrderView view = orderWithFourUnits();
        Long orderId = view.order().getId();
        OrderItem usa = view.items().get(0);
        OrderItem lima = view.items().get(1);
        OrderItem uncovered = view.items().get(2);
        stubAllocate(UNIT, Warehouse.USA);
        stubAllocate(UNIT + 1, Warehouse.LIMA);
        allocation.allocate(orderId, usa.getId(), UNIT);
        allocation.allocate(orderId, lima.getId(), UNIT + 1);
        AtomicBoolean anyInsideTx = new AtomicBoolean();
        doAnswer(inv -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                anyInsideTx.set(true);
            }
            Long itemId = inv.getArgument(1);
            if (itemId.equals(lima.getId())) {
                throw new InventoryUnavailableException("caido");
            }
            return null;
        }).when(inventoryPort).release(anyLong(), anyLong());

        OrderView result = orderUseCase.cancel(orderId);

        verify(inventoryPort).release(UNIT, usa.getId());
        verify(inventoryPort).release(UNIT + 1, lima.getId());
        verify(inventoryPort, never()).release(anyLong(), eq(uncovered.getId()));
        assertThat(anyInsideTx).isFalse();
        assertThat(result.items()).allMatch(i -> i.getStatus() == OrderItemStatus.CANCELLED);
        OrderItem usaResult = result.items().stream().filter(i -> i.getId().equals(usa.getId())).findFirst()
                .orElseThrow();
        assertThat(usaResult.isInventoryReleasePending()).isFalse();
        assertThat(reload(usa).isInventoryReleasePending()).isFalse();
        assertThat(reload(lima).isInventoryReleasePending()).isTrue();
        assertThat(reload(lima).getPendingReleaseUnitId()).isEqualTo(UNIT + 1);
        assertThat(historyCount(usa.getId(), HistoryEventType.INVENTORY_RELEASED)).isEqualTo(1);
        assertThat(historyCount(lima.getId(), HistoryEventType.INVENTORY_RELEASED)).isZero();
    }

    @Test
    void cancelUncoveredUnit_noInventoryCall() {
        OrderView view = orderWithFourUnits();
        OrderItem unit = view.items().get(0);

        itemUseCase.changeStatus(view.order().getId(), unit.getId(), OrderItemStatus.CANCELLED, null, null);

        verifyNoInteractions(inventoryPort);
        assertThat(reload(unit).isInventoryReleasePending()).isFalse();
    }
}
