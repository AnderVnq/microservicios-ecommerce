package com.example.msinventory.infrastructure.persistence;

import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort.UnitFilter;
import com.example.msinventory.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({InventoryUnitRepositoryAdapter.class, InventoryEntryRepositoryAdapter.class,
        InventoryMovementRepositoryAdapter.class, ItemConditionRepositoryAdapter.class, ConditionSeeder.class})
class InventoryUnitRepositoryAdapterTest {

    @Autowired InventoryUnitRepositoryAdapter units;
    @Autowired InventoryEntryRepositoryAdapter entries;
    @Autowired InventoryMovementRepositoryAdapter movements;
    @Autowired ItemConditionRepositoryAdapter conditions;
    @Autowired ConditionSeeder seeder;

    private Long entryId;

    @BeforeEach
    void setUp() {
        seeder.run(null);
        entryId = entries.save(InventoryEntry.builder().idempotencyKey("k-" + System.nanoTime()).requestHash("h")
                .entryType(EntryType.STOCK_PURCHASE).quantity(1).performedBy("tester")
                .createdAt(Instant.now()).build()).getId();
    }

    private InventoryUnit unit(String sku, String condition, Warehouse wh, InventoryUnitStatus status,
                               LocalDate deadline, String invoice) {
        return InventoryUnit.newUnit(sku, condition, wh, status, new BigDecimal("10.50"), "USD",
                EntryType.STOCK_PURCHASE, invoice, null, null,
                status == InventoryUnitStatus.IN_TRANSIT ? LocalDate.of(2026, 12, 1) : null, deadline, entryId);
    }

    @Test
    void seeder_createsFourConditionsOnce() {
        seeder.run(null);
        seeder.run(null);

        List<ItemCondition> all = conditions.findAll();
        assertThat(all).extracting(ItemCondition::getCode)
                .containsExactlyInAnyOrder("NEW", "OPEN_BOX", "REFURBISHED", "USED");
        assertThat(conditions.findByCode("OPEN_BOX").orElseThrow().getName()).isEqualTo("Open box");
        assertThat(conditions.findByCode("NEW").orElseThrow().getName()).isEqualTo("Nuevo");
        assertThat(all).allMatch(ItemCondition::isActive);
    }

    @Test
    void seeder_doesNotOverwriteExistingConditions() {
        conditions.save(new ItemCondition("NEW", "Nuevo renombrado", false));

        seeder.run(null);

        ItemCondition kept = conditions.findByCode("NEW").orElseThrow();
        assertThat(kept.getName()).isEqualTo("Nuevo renombrado");
        assertThat(kept.isActive()).isFalse();
        assertThat(conditions.findAll()).hasSize(4);
    }

    @Test
    void uniqueAllocatedOrderItemId_rejectsSecond() {
        InventoryUnit a = unit("SKU-1", "NEW", Warehouse.USA, InventoryUnitStatus.AVAILABLE, null, null);
        InventoryUnit b = unit("SKU-1", "NEW", Warehouse.USA, InventoryUnitStatus.AVAILABLE, null, null);
        a.allocate(77L, "SKU-1");
        b.allocate(77L, "SKU-1");

        units.save(a);
        assertThatThrownBy(() -> {
            units.save(b);
            units.findById(-1L); // fuerza flush del insert
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_updatesExistingAndRoundTripsFields() {
        InventoryUnit saved = units.save(unit("SKU-R", "USED", Warehouse.LIMA, InventoryUnitStatus.IN_TRANSIT,
                LocalDate.of(2026, 11, 30), "INV-R"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEntryId()).isEqualTo(entryId);
        assertThat(saved.getConditionCode()).isEqualTo("USED");
        assertThat(saved.getUnitCost()).isEqualByComparingTo("10.50");

        saved.arrive();
        units.save(saved);
        InventoryUnit reloaded = units.findByIdForUpdate(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InventoryUnitStatus.AVAILABLE);
        assertThat(reloaded.getArrivedAt()).isNotNull();
        assertThat(units.findByEntryId(entryId)).hasSize(1);
        assertThat(units.findByAllocatedOrderItemId(5L)).isEmpty();
    }

    @Test
    void existsOtherWithInvoice_checksUnitInvoices() {
        units.save(unit("SKU-I", "NEW", Warehouse.USA, InventoryUnitStatus.AVAILABLE, null, "INV-9"));

        assertThat(entries.existsOtherWithInvoice("INV-9")).isTrue();
        assertThat(entries.existsOtherWithInvoice("INV-10")).isFalse();
    }

    @Test
    void search_filtersAndReturnDeadline() {
        InventoryUnit u1 = units.save(unit("A", "NEW", Warehouse.USA, InventoryUnitStatus.AVAILABLE,
                LocalDate.of(2026, 11, 1), null));
        InventoryUnit u2 = units.save(unit("A", "USED", Warehouse.LIMA, InventoryUnitStatus.AVAILABLE,
                LocalDate.of(2026, 12, 1), null));
        InventoryUnit u3 = units.save(unit("B", "NEW", Warehouse.USA, InventoryUnitStatus.IN_TRANSIT, null, null));

        assertThat(ids(units.search(new UnitFilter(null, null, null, null, null))))
                .containsExactly(u1.getId(), u2.getId(), u3.getId());
        assertThat(ids(units.search(new UnitFilter("A", null, null, null, null))))
                .containsExactly(u1.getId(), u2.getId());
        assertThat(ids(units.search(new UnitFilter(null, InventoryUnitStatus.IN_TRANSIT, null, null, null))))
                .containsExactly(u3.getId());
        assertThat(ids(units.search(new UnitFilter(null, null, Warehouse.LIMA, null, null))))
                .containsExactly(u2.getId());
        assertThat(ids(units.search(new UnitFilter(null, null, null, "NEW", null))))
                .containsExactly(u1.getId(), u3.getId());
        assertThat(ids(units.search(new UnitFilter(null, null, null, null, LocalDate.of(2026, 12, 1)))))
                .containsExactly(u1.getId());
        assertThat(ids(units.search(new UnitFilter(null, null, null, null, LocalDate.of(2026, 11, 1)))))
                .isEmpty();
    }

    @Test
    void findForKardex_ordersByOccurredAtThenId() {
        Instant t0 = Instant.parse("2026-01-01T10:00:00Z");
        movements.append(mv("K", "NEW", "USD", MovementType.ARRIVAL, t0.plusSeconds(60)));
        movements.append(mv("K", "NEW", "USD", MovementType.ENTRY_STOCK_PURCHASE, t0));
        movements.append(mv("K", "USED", "USD", MovementType.ENTRY_CUSTOMER_RETURN, t0)); // mismo instante, id mayor
        movements.append(mv("K", "NEW", "PEN", MovementType.ENTRY_STOCK_PURCHASE, t0));
        movements.append(mv("OTHER", "NEW", "USD", MovementType.ENTRY_STOCK_PURCHASE, t0));

        List<InventoryMovement> all = movements.findForKardex("K", null, "USD", null);
        assertThat(all).extracting(InventoryMovement::type).containsExactly(
                MovementType.ENTRY_STOCK_PURCHASE, MovementType.ENTRY_CUSTOMER_RETURN, MovementType.ARRIVAL);
        assertThat(all.get(0).id()).isLessThan(all.get(1).id());

        assertThat(movements.findForKardex("K", "NEW", "USD", null)).hasSize(2);
        assertThat(movements.findForKardex("K", null, "USD", t0)).hasSize(2);
        assertThat(movements.findForKardex("K", null, "USD", t0.plusSeconds(60))).hasSize(3);
        assertThat(movements.findForKardex("K", null, "PEN", null)).hasSize(1);
    }

    @Test
    void append_storesDirectionFromType() {
        Long unitId = units.save(unit("D", "NEW", Warehouse.USA, InventoryUnitStatus.AVAILABLE, null, null)).getId();
        movements.append(new InventoryMovement(null, unitId, MovementType.TRANSFER, "D", "NEW",
                new BigDecimal("1.00"), "USD", Warehouse.USA, Warehouse.LIMA, "ref", "motivo", "tester",
                Instant.now()));

        List<InventoryMovement> found = movements.findByUnitId(unitId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).type().direction()).isEqualTo(Direction.INTERNAL);
        assertThat(found.get(0).fromWarehouse()).isEqualTo(Warehouse.USA);
        assertThat(found.get(0).reason()).isEqualTo("motivo");
    }

    private InventoryMovement mv(String sku, String cond, String currency, MovementType type, Instant at) {
        Long unitId = units.save(unit(sku, cond, Warehouse.USA, InventoryUnitStatus.AVAILABLE, null, null)).getId();
        return new InventoryMovement(null, unitId, type, sku, cond, new BigDecimal("10.50"), currency,
                null, Warehouse.USA, null, null, "tester", at);
    }

    private List<Long> ids(List<InventoryUnit> list) {
        return list.stream().map(InventoryUnit::getId).toList();
    }
}
