package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.PurchaseQuantityExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseTest {

    private Purchase purchase(int qty) {
        return Purchase.builder().quantity(qty).build();
    }

    @Test
    void purchase_pendingAndSetAside() {
        Purchase p = purchase(10);
        assertThat(p.pending()).isEqualTo(10);
        p.registerArrival(4, 0);
        assertThat(p.pending()).isEqualTo(6);
        p.registerNotReceived(2, 0);
        assertThat(p.pending()).isEqualTo(4);
        assertThat(p.getReceivedQuantity()).isEqualTo(4);
        assertThat(p.getNotReceivedQuantity()).isEqualTo(2);
        p.registerArrival(3, 1);
        assertThat(p.pending()).isEqualTo(1);
        assertThatThrownBy(() -> p.registerArrival(1, 1)).isInstanceOf(PurchaseQuantityExceededException.class);
    }

    @Test
    void purchase_arrivalOverPending_throws() {
        Purchase p = purchase(5);
        assertThatThrownBy(() -> p.registerArrival(6, 0)).isInstanceOf(PurchaseQuantityExceededException.class);
        assertThatThrownBy(() -> p.registerArrival(0, 0)).isInstanceOf(PurchaseQuantityExceededException.class);
        assertThatThrownBy(() -> p.registerNotReceived(6, 0)).isInstanceOf(PurchaseQuantityExceededException.class);
        assertThat(p.pending()).isEqualTo(5);
    }

    @Test
    void linkInventoryEntry_sets() {
        Purchase p = purchase(1);
        p.linkInventoryEntry(9L);
        assertThat(p.getInventoryEntryId()).isEqualTo(9L);
    }
}
