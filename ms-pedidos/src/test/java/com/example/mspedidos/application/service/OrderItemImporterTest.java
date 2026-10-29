package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.domain.exception.ImporterChangeNotAllowedException;
import com.example.mspedidos.domain.exception.QuotaExceededException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderItemImporterTest extends ServiceTestSupport {

    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;
    @Autowired
    QuotaAllocator quotaAllocator;

    private static final AssignImporterCommand FAMILY_7 = new AssignImporterCommand(ImporterType.FAMILY_MEMBER, 7L);
    private static final AssignImporterCommand HOLDER = new AssignImporterCommand(ImporterType.HOLDER, null);

    /** Orden del titular 1 (DNI 111) con 4 unidades: 3 HOLDER y la ultima UNASSIGNED con incidencia. */
    private OrderView orderWithOnePendingUnit() {
        stubHolder(1L, dni("111"));
        return orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(4, "10.00")));
    }

    private OrderItem pendingUnit(OrderView view) {
        return view.items().stream().filter(i -> i.getImporterType() == ImporterType.UNASSIGNED)
                .findFirst().orElseThrow();
    }

    @Test
    void assignFamilyMember_withQuota_resolvesIncident() {
        OrderView view = orderWithOnePendingUnit();
        OrderItem unit = pendingUnit(view);
        stubFamilyMember(1L, 7L, dni("333"));
        ItemIncident incident = incidentRepository.findOpen(unit.getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING)
                .orElseThrow();

        OrderItem result = itemUseCase.assignImporter(view.order().getId(), unit.getId(), FAMILY_7);

        assertThat(result.getImporterType()).isEqualTo(ImporterType.FAMILY_MEMBER);
        assertThat(result.getFamilyMemberId()).isEqualTo(7L);
        assertThat(result.getImporterDocument()).isEqualTo(dni("333"));
        OrderItem stored = itemRepository.findByIdAndOrderId(unit.getId(), view.order().getId()).orElseThrow();
        assertThat(stored.getImporterType()).isEqualTo(ImporterType.FAMILY_MEMBER);
        assertThat(stored.getImporterDocument()).isEqualTo(dni("333"));

        ItemIncident resolved = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedBy()).isEqualTo("op-1");

        List<OrderItemHistoryEntry> history = historyRepository.findByOrderItemId(unit.getId());
        assertThat(history).extracting(OrderItemHistoryEntry::eventType)
                .contains(HistoryEventType.IMPORTER_ASSIGNED, HistoryEventType.INCIDENT_RESOLVED);
        OrderItemHistoryEntry assigned = history.stream()
                .filter(h -> h.eventType() == HistoryEventType.IMPORTER_ASSIGNED).findFirst().orElseThrow();
        assertThat(assigned.fromValue()).isEqualTo("UNASSIGNED");
        assertThat(assigned.toValue()).isEqualTo("FAMILY_MEMBER:333");
        assertThat(assigned.performedBy()).isEqualTo("op-1");
        assertThat(quotaAllocator.available(dni("333"), YEAR, null)).isEqualTo(2);
    }

    @Test
    void assignFamilyMember_withoutQuota_conflict() {
        OrderView view = orderWithOnePendingUnit();
        OrderItem unit = pendingUnit(view);
        stubFamilyMember(1L, 7L, dni("333"));
        seedUsedUnits(dni("333"), ImporterType.HOLDER, ORDER_DATE, 3);

        assertThatThrownBy(() -> itemUseCase.assignImporter(view.order().getId(), unit.getId(), FAMILY_7))
                .isInstanceOf(QuotaExceededException.class);

        OrderItem stored = itemRepository.findByIdAndOrderId(unit.getId(), view.order().getId()).orElseThrow();
        assertThat(stored.getImporterType()).isEqualTo(ImporterType.UNASSIGNED);
        assertThat(stored.getImporterDocument()).isNull();
        assertThat(incidentRepository.findOpen(unit.getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING)).isPresent();
        assertThat(historyCount(unit.getId(), HistoryEventType.IMPORTER_ASSIGNED)).isZero();
    }

    @Test
    void familyMemberSharingHolderDniSharesQuota() {
        // orden A: el titular 1 (DNI 111) consume sus 3 unidades
        stubHolder(1L, dni("111"));
        orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(3, "10.00")));
        // orden B del cliente 2 (DNI 222) con una unidad pendiente; su familiar 7 es la misma persona (DNI 111)
        stubHolder(2L, dni("222"));
        OrderView orderB = orderUseCase.create(command(2L, uniqueExternalId(), ORDER_DATE, line(4, "10.00")));
        OrderItem unit = pendingUnit(orderB);
        stubFamilyMember(2L, 7L, dni("111"));

        assertThatThrownBy(() -> itemUseCase.assignImporter(orderB.order().getId(), unit.getId(), FAMILY_7))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void reassignToSamePersonDoesNotNeedExtraSlot() {
        OrderView view = orderWithOnePendingUnit();
        OrderItem unit = pendingUnit(view);
        stubFamilyMember(1L, 7L, dni("333"));
        seedUsedUnits(dni("333"), ImporterType.HOLDER, ORDER_DATE, 2);
        itemUseCase.assignImporter(view.order().getId(), unit.getId(), FAMILY_7);
        assertThat(quotaAllocator.available(dni("333"), YEAR, null)).isZero();

        OrderItem again = itemUseCase.assignImporter(view.order().getId(), unit.getId(), FAMILY_7);

        assertThat(again.getImporterType()).isEqualTo(ImporterType.FAMILY_MEMBER);
        assertThat(again.getImporterDocument()).isEqualTo(dni("333"));
        assertThat(quotaAllocator.available(dni("333"), YEAR, null)).isZero();
    }

    @Test
    void reassignHolderUnitToSameHolder_atLimit_succeeds() {
        OrderView view = orderWithOnePendingUnit();
        OrderItem holderUnit = view.items().get(0);
        assertThat(holderUnit.getImporterType()).isEqualTo(ImporterType.HOLDER);

        OrderItem again = itemUseCase.assignImporter(view.order().getId(), holderUnit.getId(), HOLDER);

        assertThat(again.getImporterType()).isEqualTo(ImporterType.HOLDER);
        OrderItemHistoryEntry last = historyRepository.findByOrderItemId(holderUnit.getId()).stream()
                .filter(h -> h.eventType() == HistoryEventType.IMPORTER_ASSIGNED).reduce((a, b) -> b).orElseThrow();
        assertThat(last.fromValue()).isEqualTo("HOLDER:111");
        assertThat(last.toValue()).isEqualTo("HOLDER:111");
    }

    @Test
    void reassignFromHolderToFamilyMember_freesHolderSlot() {
        OrderView view = orderWithOnePendingUnit();
        OrderItem holderUnit = view.items().get(0);
        stubFamilyMember(1L, 7L, dni("333"));

        itemUseCase.assignImporter(view.order().getId(), holderUnit.getId(), FAMILY_7);

        assertThat(quotaAllocator.available(dni("111"), YEAR, null)).isEqualTo(1);
        assertThat(quotaAllocator.available(dni("333"), YEAR, null)).isEqualTo(2);
    }

    @Test
    void assignUnassigned_rejected() {
        OrderView view = orderWithOnePendingUnit();
        OrderItem unit = pendingUnit(view);

        assertThatThrownBy(() -> itemUseCase.assignImporter(view.order().getId(), unit.getId(),
                new AssignImporterCommand(ImporterType.UNASSIGNED, null)))
                .isInstanceOf(ImporterChangeNotAllowedException.class);
        verify(clientesPort, never()).getFamilyMember(any(), any());
    }

    @Test
    void cancelledUnitFreesQuota() {
        stubHolder(1L, dni("111"));
        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(3, "10.00")));
        assertThat(quotaAllocator.available(dni("111"), YEAR, null)).isZero();
        OrderItem unit = view.items().get(0);

        unit.cancel();
        itemRepository.save(unit);

        assertThat(quotaAllocator.available(dni("111"), YEAR, null)).isEqualTo(1);
    }

    @Test
    void newYearResetsQuota() {
        seedUsedUnits(dni("111"), ImporterType.HOLDER, ORDER_DATE, 3);
        stubHolder(1L, dni("111"));

        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(),
                LocalDateTime.of(2027, 1, 2, 9, 0), line(3, "10.00")));

        assertThat(view.items()).extracting(OrderItem::getImporterType).containsOnly(ImporterType.HOLDER);
        assertThat(quotaAllocator.available(dni("111"), YEAR, null)).isZero();
        assertThat(quotaAllocator.available(dni("111"), 2027, null)).isZero();
    }

    @Test
    void addUnits_appliesQuotaAndRecalculatesTotal() {
        stubHolder(1L, dni("111"));
        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(2, "10.00")));

        List<OrderItem> added = itemUseCase.addUnits(view.order().getId(), line(2, "5.00"));

        assertThat(added).hasSize(2).allSatisfy(i -> {
            assertThat(i.getOrderId()).isEqualTo(view.order().getId());
            assertThat(historyCount(i.getId(), HistoryEventType.CREATED)).isEqualTo(1);
        });
        assertThat(added).extracting(OrderItem::getImporterType)
                .containsExactly(ImporterType.HOLDER, ImporterType.UNASSIGNED);
        assertThat(incidentRepository.findOpen(added.get(1).getId(), IncidentType.IMPORTER_ASSIGNMENT_PENDING))
                .isPresent();
        assertThat(itemRepository.findByOrderId(view.order().getId())).hasSize(4);
        assertThat(orderRepository.findById(view.order().getId()).orElseThrow().getItemsTotal())
                .isEqualByComparingTo("30.00");
    }
}
