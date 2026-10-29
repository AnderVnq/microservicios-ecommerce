package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.SelfApprovalNotAllowedException;
import com.example.mspedidos.domain.exception.StatusChangeRequestAlreadyDecidedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusChangeRequestTest {

    private StatusChangeRequest open() {
        return StatusChangeRequest.openAllocationUndo(1L, 2L, "error de asignacion", "alice");
    }

    @Test
    void open_isPendingWithRequester() {
        StatusChangeRequest r = open();
        assertThat(r.getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
        assertThat(r.getRequestedBy()).isEqualTo("alice");
        assertThat(r.getRequestedAt()).isNotNull();
        assertThat(r.getDecidedAt()).isNull();
    }

    @Test
    void open_blankReason_throws() {
        assertThatThrownBy(() -> StatusChangeRequest.openAllocationUndo(1L, 2L, " ", "alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approve_byOther_setsDecision() {
        StatusChangeRequest r = open();
        r.approve("bob", "ok");
        assertThat(r.getStatus()).isEqualTo(StatusChangeRequestStatus.APPROVED);
        assertThat(r.getDecidedBy()).isEqualTo("bob");
        assertThat(r.getDecisionNote()).isEqualTo("ok");
        assertThat(r.getDecidedAt()).isNotNull();
    }

    @Test
    void selfApproval_throws() {
        StatusChangeRequest r = open();
        assertThatThrownBy(() -> r.approve("alice", "ok")).isInstanceOf(SelfApprovalNotAllowedException.class);
        assertThat(r.getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
    }

    @Test
    void reject_setsDecision() {
        StatusChangeRequest r = open();
        r.reject("bob", "no procede");
        assertThat(r.getStatus()).isEqualTo(StatusChangeRequestStatus.REJECTED);
        assertThat(r.getDecisionNote()).isEqualTo("no procede");
    }

    @Test
    void rejectWithoutNote_throws() {
        StatusChangeRequest r = open();
        assertThatThrownBy(() -> r.reject("bob", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.reject("bob", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(r.getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
    }

    @Test
    void approveTwice_throws() {
        StatusChangeRequest r = open();
        r.approve("bob", "ok");
        assertThatThrownBy(() -> r.approve("carol", "ok")).isInstanceOf(StatusChangeRequestAlreadyDecidedException.class);
        assertThatThrownBy(() -> r.reject("carol", "no")).isInstanceOf(StatusChangeRequestAlreadyDecidedException.class);
    }

    @Test
    void open_blankActor_throws() {
        assertThatThrownBy(() -> StatusChangeRequest.openAllocationUndo(1L, 2L, "motivo", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StatusChangeRequest.openAllocationUndo(1L, 2L, "motivo", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void openNotReceived_requiresQuantityAndReason() {
        StatusChangeRequest r = StatusChangeRequest.openNotReceived(7L, 3, "no llego", "alice");
        assertThat(r.getType()).isEqualTo(StatusChangeRequestType.PURCHASE_NOT_RECEIVED);
        assertThat(r.getPurchaseId()).isEqualTo(7L);
        assertThat(r.getQuantity()).isEqualTo(3);
        assertThat(r.getStatus()).isEqualTo(StatusChangeRequestStatus.PENDING);
        assertThatThrownBy(() -> StatusChangeRequest.openNotReceived(7L, 0, "x", "alice"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StatusChangeRequest.openNotReceived(7L, null, "x", "alice"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StatusChangeRequest.openNotReceived(7L, 1, " ", "alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markAppliedDirectly_setsStatus() {
        StatusChangeRequest r = StatusChangeRequest.openNotReceived(7L, 3, "no llego", "alice");
        r.markAppliedDirectly("alice");
        assertThat(r.getStatus()).isEqualTo(StatusChangeRequestStatus.APPLIED_DIRECTLY);
        assertThat(r.getDecidedAt()).isNotNull();
    }
}
