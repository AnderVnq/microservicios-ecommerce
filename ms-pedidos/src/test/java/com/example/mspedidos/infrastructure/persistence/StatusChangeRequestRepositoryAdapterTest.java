package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(StatusChangeRequestRepositoryAdapter.class)
class StatusChangeRequestRepositoryAdapterTest {

    @Autowired
    StatusChangeRequestRepositoryPort requests;

    @Test
    void statusChangeRequests_sumPendingNotReceived() {
        StatusChangeRequest three = requests.save(StatusChangeRequest.openNotReceived(1L, 3, "perdido", "ana"));
        requests.save(StatusChangeRequest.openNotReceived(1L, 4, "danado", "ana"));
        requests.save(StatusChangeRequest.openNotReceived(2L, 9, "otro", "ana"));
        StatusChangeRequest decided = requests.save(StatusChangeRequest.openNotReceived(1L, 5, "x", "ana"));
        decided.approve("bob", null);
        requests.save(decided);

        assertThat(requests.sumPendingNotReceived(1L)).isEqualTo(7);
        assertThat(requests.sumPendingNotReceived(99L)).isZero();
        assertThat(requests.sumPendingNotReceivedExcluding(1L, three.getId())).isEqualTo(4);
        assertThat(requests.sumPendingNotReceivedExcluding(1L, null)).isEqualTo(7);
        assertThat(requests.findPendingByPurchaseId(2L)).isPresent();
        assertThat(requests.findPendingByPurchaseId(99L)).isEmpty();
    }

    @Test
    void roundTripsBothTypesAndSearches() {
        StatusChangeRequest undo = requests.save(StatusChangeRequest.openAllocationUndo(10L, 20L, "motivo", "ana"));
        StatusChangeRequest nr = requests.save(StatusChangeRequest.openNotReceived(1L, 3, "perdido", "ana"));

        StatusChangeRequest found = requests.findByIdForUpdate(nr.getId()).orElseThrow();
        assertThat(found.getType()).isEqualTo(StatusChangeRequestType.PURCHASE_NOT_RECEIVED);
        assertThat(found.getPurchaseId()).isEqualTo(1L);
        assertThat(found.getQuantity()).isEqualTo(3);
        assertThat(found.getOrderItemId()).isNull();
        assertThat(requests.findById(undo.getId()).orElseThrow().getInventoryUnitId()).isEqualTo(20L);

        assertThat(requests.search(null, null)).hasSize(2);
        assertThat(requests.search(StatusChangeRequestStatus.PENDING, StatusChangeRequestType.ALLOCATION_UNDO))
                .extracting(StatusChangeRequest::getId).containsExactly(undo.getId());
        assertThat(requests.search(StatusChangeRequestStatus.APPROVED, null)).isEmpty();
        assertThat(requests.findPendingByOrderItemId(10L)).isPresent();
    }
}
