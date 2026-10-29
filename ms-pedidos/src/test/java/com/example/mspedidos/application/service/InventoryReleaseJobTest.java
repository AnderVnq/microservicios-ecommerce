package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.InventoryAllocationUseCase;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.out.InventoryPort.AllocatedUnit;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Warehouse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReleaseJobTest extends ServiceTestSupport {

    private static final long UNIT_A = 601L;
    private static final long UNIT_B = 602L;

    @MockitoSpyBean
    OrderItemRepositoryPort spiedItems;
    @Autowired
    InventoryAllocationUseCase allocation;
    @Autowired
    InventoryReleaseJob job;
    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;

    private OrderItem reload(OrderItem item) {
        return itemRepository.findByIdAndOrderId(item.getId(), item.getOrderId()).orElseThrow();
    }

    /** Dos unidades cubiertas desde inventario y canceladas con ms-inventory caido: ambas quedan pendientes. */
    private OrderView twoCancelledPendingUnits() {
        stubHolder(1L, dni("111"));
        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(3, "10.00")));
        Long orderId = view.order().getId();
        when(inventoryPort.allocate(eq(UNIT_A), anyLong(), anyString()))
                .thenReturn(new AllocatedUnit(UNIT_A, Warehouse.USA, "NEW", BigDecimal.TEN));
        when(inventoryPort.allocate(eq(UNIT_B), anyLong(), anyString()))
                .thenReturn(new AllocatedUnit(UNIT_B, Warehouse.LIMA, "NEW", BigDecimal.TEN));
        allocation.allocate(orderId, view.items().get(0).getId(), UNIT_A);
        allocation.allocate(orderId, view.items().get(1).getId(), UNIT_B);
        doThrow(new InventoryUnavailableException("caido")).when(inventoryPort).release(anyLong(), anyLong());
        itemUseCase.changeStatus(orderId, view.items().get(0).getId(), OrderItemStatus.CANCELLED, null, null);
        itemUseCase.changeStatus(orderId, view.items().get(1).getId(), OrderItemStatus.CANCELLED, null, null);
        assertThat(reload(view.items().get(0)).isInventoryReleasePending()).isTrue();
        assertThat(reload(view.items().get(1)).isInventoryReleasePending()).isTrue();
        clearInvocations(inventoryPort);
        return view;
    }

    @Test
    void job_retriesPendingAndClearsFlag() {
        OrderView view = twoCancelledPendingUnits();
        OrderItem a = view.items().get(0);
        OrderItem b = view.items().get(1);
        OrderItem untouched = view.items().get(2);
        // ms-inventory vuelve para A pero sigue fallando para B
        doNothing().when(inventoryPort).release(UNIT_A, a.getId());
        when(actorProvider.currentActor()).thenReturn("system");

        allocation.retryPendingReleases();

        verify(inventoryPort).release(UNIT_A, a.getId());
        verify(inventoryPort).release(UNIT_B, b.getId());
        verify(inventoryPort, never()).release(anyLong(), eq(untouched.getId()));
        OrderItem storedA = reload(a);
        assertThat(storedA.isInventoryReleasePending()).isFalse();
        assertThat(storedA.getPendingReleaseUnitId()).isNull();
        assertThat(storedA.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        OrderItemHistoryEntry released = historyRepository.findByOrderItemId(a.getId()).stream()
                .filter(h -> h.eventType() == HistoryEventType.INVENTORY_RELEASED).findFirst().orElseThrow();
        assertThat(released.fromValue()).isEqualTo("unit:" + UNIT_A);
        assertThat(released.toValue()).isNull();
        assertThat(released.performedBy()).isEqualTo("system");
        OrderItem storedB = reload(b);
        assertThat(storedB.isInventoryReleasePending()).isTrue();
        assertThat(storedB.getPendingReleaseUnitId()).isEqualTo(UNIT_B);
        assertThat(historyCount(b.getId(), HistoryEventType.INVENTORY_RELEASED)).isZero();
        assertThat(itemRepository.findReleasePending()).extracting(OrderItem::getId).containsExactly(b.getId());

        // la siguiente pasada del job (via el bean programado) libera B; A ya no se vuelve a llamar
        clearInvocations(inventoryPort);
        doNothing().when(inventoryPort).release(UNIT_B, b.getId());
        job.retryPendingReleases();

        verify(inventoryPort, never()).release(eq(UNIT_A), anyLong());
        verify(inventoryPort).release(UNIT_B, b.getId());
        assertThat(reload(b).isInventoryReleasePending()).isFalse();
        assertThat(itemRepository.findReleasePending()).isEmpty();
        assertThat(historyCount(a.getId(), HistoryEventType.INVENTORY_RELEASED)).isEqualTo(1);
        assertThat(historyCount(b.getId(), HistoryEventType.INVENTORY_RELEASED)).isEqualTo(1);
    }

    @Test
    void job_staleSnapshot_itemNoLongerPendingForThatUnit_noRemoteRelease() {
        OrderView view = twoCancelledPendingUnits();
        OrderItem a = view.items().get(0);
        OrderItem b = view.items().get(1);
        doNothing().when(inventoryPort).release(anyLong(), anyLong());
        // el job toma su lista y, antes de llamar a inventario, A ya se libero y apunta a otra unidad, y B ya no
        // esta pendiente pero volvio a cubrirse con la misma unidad (deshacer + reasignar)
        doAnswer(inv -> {
            Object snapshot = inv.callRealMethod();
            jdbc.update("update order_items set pending_release_unit_id = ? where id = ?", 999L, a.getId());
            jdbc.update("update order_items set inventory_release_pending = false, pending_release_unit_id = null, "
                    + "inventory_unit_id = ? where id = ?", UNIT_B, b.getId());
            return snapshot;
        }).when(spiedItems).findReleasePending();

        allocation.retryPendingReleases();

        verify(inventoryPort, never()).release(eq(UNIT_A), anyLong());
        verify(inventoryPort, never()).release(eq(UNIT_B), anyLong());
        // la marca nueva de A (unidad 999) la toma la siguiente pasada, con una foto fresca
        verify(inventoryPort, never()).release(eq(999L), anyLong());
        assertThat(reload(a).getPendingReleaseUnitId()).isEqualTo(999L);
        assertThat(reload(a).isInventoryReleasePending()).isTrue();
        assertThat(reload(b).getInventoryUnitId()).isEqualTo(UNIT_B);
        assertThat(reload(b).isInventoryReleasePending()).isFalse();
    }

    @Test
    void job_isScheduledWithConfiguredDelay() throws Exception {
        Scheduled scheduled = InventoryReleaseJob.class.getDeclaredMethod("retryPendingReleases")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${orders.inventory-release-job.delay-ms}");
    }
}
