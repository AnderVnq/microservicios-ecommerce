package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.InventorySyncState;
import com.example.mspedidos.application.port.out.InventorySyncTaskRepositoryPort;
import com.example.mspedidos.application.port.out.PurchaseFilter;
import com.example.mspedidos.application.port.out.PurchaseRepositoryPort;
import com.example.mspedidos.application.port.out.SupplierRepositoryPort;
import com.example.mspedidos.domain.model.InventorySyncTask;
import com.example.mspedidos.domain.model.InventorySyncTaskStatus;
import com.example.mspedidos.domain.model.InventorySyncTaskType;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.PurchaseArrival;
import com.example.mspedidos.domain.model.PurchaseNonReceipt;
import com.example.mspedidos.domain.model.Supplier;
import com.example.mspedidos.domain.model.SupplierStatus;
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
@Import({SupplierRepositoryAdapter.class, PurchaseRepositoryAdapter.class, InventorySyncTaskRepositoryAdapter.class})
class PurchaseRepositoryAdapterTest {

    @Autowired
    SupplierRepositoryPort suppliers;
    @Autowired
    PurchaseRepositoryPort purchases;
    @Autowired
    InventorySyncTaskRepositoryPort tasks;

    private Supplier supplier(String name, String country) {
        return suppliers.save(Supplier.create(name, country, "Addr", "desc"));
    }

    private Purchase purchase(Long supplierId, String key, String invoice) {
        return purchases.save(Purchase.builder()
                .idempotencyKey(key).requestHash("h").supplierId(supplierId).sku("SKU-1").conditionCode("NEW")
                .quantity(10).unitCost(new BigDecimal("5.50")).costCurrency("USD").supplierPurchaseId("SP-1")
                .invoiceNumber(invoice).expectedArrivalDate(LocalDate.of(2026, 11, 1)).createdBy("ana")
                .build());
    }

    private void task(Long purchaseId, InventorySyncTaskStatus status) {
        tasks.save(InventorySyncTask.builder().purchaseId(purchaseId).type(InventorySyncTaskType.CREATE_ENTRY)
                .payload("{}").idempotencyKey("k-" + System.nanoTime()).status(status).build());
    }

    @Test
    void supplier_nameUniqueIgnoringCase() {
        Supplier acme = supplier("Acme", "US");

        assertThat(suppliers.existsByNameIgnoreCase("ACME", null)).isTrue();
        assertThat(suppliers.existsByNameIgnoreCase("ACME", acme.getId())).isFalse();
        assertThat(suppliers.existsByNameIgnoreCase("Other", null)).isFalse();
        assertThatThrownBy(() -> supplier("acme", "PE")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void supplier_searchFiltersAndRoundTrips() {
        Supplier acme = supplier("Acme Corp", "US");
        Supplier inactive = supplier("Beta", "CN");
        inactive.update("Beta", "CN", null, null, SupplierStatus.INACTIVE);
        suppliers.save(inactive);

        assertThat(suppliers.findById(acme.getId()).orElseThrow().getCreatedAt()).isNotNull();
        assertThat(suppliers.search(null, null, null)).hasSize(2);
        assertThat(suppliers.search(SupplierStatus.INACTIVE, null, null)).extracting(Supplier::getName)
                .containsExactly("Beta");
        assertThat(suppliers.search(null, "US", null)).extracting(Supplier::getName).containsExactly("Acme Corp");
        assertThat(suppliers.search(null, null, "corp")).extracting(Supplier::getName).containsExactly("Acme Corp");
    }

    @Test
    void purchase_uniqueIdempotencyKey() {
        Supplier s = supplier("Acme", "US");
        purchase(s.getId(), "key-1", null);

        assertThat(purchases.findByIdempotencyKey("key-1")).isPresent();
        assertThatThrownBy(() -> purchase(s.getId(), "key-1", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsOtherWithInvoice_sameSupplierOnly() {
        Supplier a = supplier("A", "US");
        Supplier b = supplier("B", "US");
        Purchase first = purchase(a.getId(), "k1", "INV-1");

        assertThat(purchases.existsOtherWithInvoice(a.getId(), "INV-1", null)).isTrue();
        assertThat(purchases.existsOtherWithInvoice(a.getId(), "INV-1", first.getId())).isFalse();
        assertThat(purchases.existsOtherWithInvoice(b.getId(), "INV-1", null)).isFalse();
        assertThat(purchases.existsOtherWithInvoice(a.getId(), "INV-2", null)).isFalse();
    }

    @Test
    void purchase_roundTripAndForUpdate() {
        Supplier s = supplier("A", "US");
        Purchase saved = purchase(s.getId(), "k1", "INV-1");
        saved.registerArrival(4, 0);
        saved.linkInventoryEntry(77L);
        purchases.save(saved);

        Purchase found = purchases.findByIdForUpdate(saved.getId()).orElseThrow();
        assertThat(found.getReceivedQuantity()).isEqualTo(4);
        assertThat(found.getInventoryEntryId()).isEqualTo(77L);
        assertThat(found.getUnitCost()).isEqualByComparingTo("5.50");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(purchases.findById(saved.getId())).isPresent();
    }

    @Test
    void purchase_searchByFiltersAndInventorySync() {
        Supplier s = supplier("A", "US");
        Purchase ok = purchase(s.getId(), "k1", "INV-1");
        Purchase pending = purchase(s.getId(), "k2", "INV-2");
        Purchase failed = purchase(s.getId(), "k3", null);
        task(ok.getId(), InventorySyncTaskStatus.DONE);
        task(pending.getId(), InventorySyncTaskStatus.PENDING);
        task(failed.getId(), InventorySyncTaskStatus.PENDING);
        task(failed.getId(), InventorySyncTaskStatus.FAILED);
        ok.registerArrival(10, 0);
        purchases.save(ok);

        assertThat(purchases.search(new PurchaseFilter(null, null, null, null, null, null))).hasSize(3);
        assertThat(purchases.search(new PurchaseFilter(s.getId(), "SKU-1", "INV-2", "SP-1", null, null)))
                .extracting(Purchase::getId).containsExactly(pending.getId());
        assertThat(purchases.search(new PurchaseFilter(null, null, null, null, false, null)))
                .extracting(Purchase::getId).containsExactly(ok.getId());
        assertThat(purchases.search(new PurchaseFilter(null, null, null, null, true, null))).hasSize(2);
        assertThat(purchases.search(new PurchaseFilter(null, null, null, null, null, InventorySyncState.OK)))
                .extracting(Purchase::getId).containsExactly(ok.getId());
        assertThat(purchases.search(new PurchaseFilter(null, null, null, null, null, InventorySyncState.PENDING)))
                .extracting(Purchase::getId).containsExactly(pending.getId());
        assertThat(purchases.search(new PurchaseFilter(null, null, null, null, null, InventorySyncState.FAILED)))
                .extracting(Purchase::getId).containsExactly(failed.getId());
    }

    @Test
    void arrivalsAndNonReceipts_roundTrip() {
        Supplier s = supplier("A", "US");
        Purchase p = purchase(s.getId(), "k1", null);

        purchases.saveArrival(PurchaseArrival.builder().purchaseId(p.getId()).idempotencyKey("a1").requestHash("h")
                .quantity(3).orderItemIds(List.of(5L, 6L)).surplusQuantity(1).createdBy("ana").build());
        purchases.saveArrival(PurchaseArrival.builder().purchaseId(p.getId()).idempotencyKey("a2").requestHash("h")
                .quantity(1).orderItemIds(List.of()).surplusQuantity(1).createdBy("ana").build());
        purchases.saveNonReceipt(PurchaseNonReceipt.builder().purchaseId(p.getId()).quantity(2)
                .orderItemIds(List.of(7L)).surplusQuantity(1).reason("perdido").appliedBy("ana")
                .appliedAt(Instant.now()).build());

        assertThat(purchases.findArrivalByKey("a1").orElseThrow().getOrderItemIds()).containsExactly(5L, 6L);
        assertThat(purchases.findArrivalByKey("a2").orElseThrow().getOrderItemIds()).isEmpty();
        assertThat(purchases.findArrivalByKey("zz")).isEmpty();
        assertThat(purchases.findArrivals(p.getId())).hasSize(2);
        assertThat(purchases.findNonReceipts(p.getId())).singleElement()
                .satisfies(n -> assertThat(n.getOrderItemIds()).containsExactly(7L));
        assertThatThrownBy(() -> purchases.saveArrival(PurchaseArrival.builder().purchaseId(p.getId())
                .idempotencyKey("a1").requestHash("h").quantity(1).orderItemIds(List.of()).createdBy("x").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
