package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.SelfApprovalNotAllowedException;
import com.example.mspedidos.domain.exception.UndoRequestAlreadyDecidedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllocationUndoRequestTest {

    private AllocationUndoRequest open() {
        return AllocationUndoRequest.open(1L, 2L, "error de asignacion", "alice");
    }

    @Test
    void open_isPendingWithRequester() {
        AllocationUndoRequest r = open();
        assertThat(r.getStatus()).isEqualTo(UndoRequestStatus.PENDING);
        assertThat(r.getRequestedBy()).isEqualTo("alice");
        assertThat(r.getRequestedAt()).isNotNull();
        assertThat(r.getDecidedAt()).isNull();
    }

    @Test
    void open_blankReason_throws() {
        assertThatThrownBy(() -> AllocationUndoRequest.open(1L, 2L, " ", "alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approve_byOther_setsDecision() {
        AllocationUndoRequest r = open();
        r.approve("bob", "ok");
        assertThat(r.getStatus()).isEqualTo(UndoRequestStatus.APPROVED);
        assertThat(r.getDecidedBy()).isEqualTo("bob");
        assertThat(r.getDecisionNote()).isEqualTo("ok");
        assertThat(r.getDecidedAt()).isNotNull();
    }

    @Test
    void selfApproval_throws() {
        AllocationUndoRequest r = open();
        assertThatThrownBy(() -> r.approve("alice", "ok")).isInstanceOf(SelfApprovalNotAllowedException.class);
        assertThat(r.getStatus()).isEqualTo(UndoRequestStatus.PENDING);
    }

    @Test
    void reject_setsDecision() {
        AllocationUndoRequest r = open();
        r.reject("bob", "no procede");
        assertThat(r.getStatus()).isEqualTo(UndoRequestStatus.REJECTED);
        assertThat(r.getDecisionNote()).isEqualTo("no procede");
    }

    @Test
    void rejectWithoutNote_throws() {
        AllocationUndoRequest r = open();
        assertThatThrownBy(() -> r.reject("bob", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.reject("bob", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(r.getStatus()).isEqualTo(UndoRequestStatus.PENDING);
    }

    @Test
    void approveTwice_throws() {
        AllocationUndoRequest r = open();
        r.approve("bob", "ok");
        assertThatThrownBy(() -> r.approve("carol", "ok")).isInstanceOf(UndoRequestAlreadyDecidedException.class);
        assertThatThrownBy(() -> r.reject("carol", "no")).isInstanceOf(UndoRequestAlreadyDecidedException.class);
    }

    @Test
    void open_blankActor_throws() {
        assertThatThrownBy(() -> AllocationUndoRequest.open(1L, 2L, "motivo", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AllocationUndoRequest.open(1L, 2L, "motivo", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
