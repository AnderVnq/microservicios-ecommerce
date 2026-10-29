package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.AllocationUndoUseCase;
import com.example.mspedidos.domain.exception.ApprovalForbiddenException;
import com.example.mspedidos.domain.exception.SelfApprovalNotAllowedException;
import com.example.mspedidos.domain.exception.UndoRequestAlreadyDecidedException;
import com.example.mspedidos.domain.exception.UndoRequestNotFoundException;
import com.example.mspedidos.domain.model.AllocationUndoRequest;
import com.example.mspedidos.domain.model.UndoRequestStatus;
import com.example.mspedidos.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AllocationUndoRequestController.class)
@Import(SecurityConfig.class)
class AllocationUndoRequestControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AllocationUndoUseCase undo;
    @MockitoBean JwtDecoder jwtDecoder;

    private static AllocationUndoRequest decided(UndoRequestStatus status) {
        return AllocationUndoRequest.builder().id(3L).orderItemId(1L).inventoryUnitId(99L).reason("mal cubierta")
                .status(status).requestedBy("u1").decidedBy("admin").decisionNote("ok")
                .requestedAt(Instant.parse("2026-03-01T10:00:00Z")).decidedAt(Instant.parse("2026-03-02T10:00:00Z"))
                .build();
    }

    @Test
    void search_withStatus_mapsAllFields() throws Exception {
        when(undo.search(UndoRequestStatus.APPROVED)).thenReturn(List.of(decided(UndoRequestStatus.APPROVED)));

        mvc.perform(get("/api/v1/allocation-undo-requests").param("status", "APPROVED").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].orderItemId").value(1))
                .andExpect(jsonPath("$[0].inventoryUnitId").value(99))
                .andExpect(jsonPath("$[0].reason").value("mal cubierta"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].requestedBy").value("u1"))
                .andExpect(jsonPath("$[0].decidedBy").value("admin"))
                .andExpect(jsonPath("$[0].decisionNote").value("ok"))
                .andExpect(jsonPath("$[0].requestedAt").value("2026-03-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].decidedAt").value("2026-03-02T10:00:00Z"));
    }

    @Test
    void search_withoutStatus_passesNull() throws Exception {
        when(undo.search(null)).thenReturn(List.of());
        mvc.perform(get("/api/v1/allocation-undo-requests").with(jwt())).andExpect(status().isOk());
        verify(undo).search(null);
    }

    @Test
    void search_unknownStatus_422() throws Exception {
        mvc.perform(get("/api/v1/allocation-undo-requests").param("status", "NOPE").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void approve_200_withNote() throws Exception {
        when(undo.approve(3L, "ok")).thenReturn(decided(UndoRequestStatus.APPROVED));
        mvc.perform(post("/api/v1/allocation-undo-requests/3/approve").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_noBody_200_nullNote() throws Exception {
        when(undo.approve(3L, null)).thenReturn(decided(UndoRequestStatus.APPROVED));
        mvc.perform(post("/api/v1/allocation-undo-requests/3/approve").with(jwt()))
                .andExpect(status().isOk());
        verify(undo).approve(3L, null);
    }

    @Test
    void approve_forbidden_403_APPROVAL_FORBIDDEN() throws Exception {
        when(undo.approve(3L, null)).thenThrow(new ApprovalForbiddenException("solo admin"));
        mvc.perform(post("/api/v1/allocation-undo-requests/3/approve").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_FORBIDDEN"));
    }

    @Test
    void approve_selfApproval_409() throws Exception {
        when(undo.approve(3L, null)).thenThrow(new SelfApprovalNotAllowedException("propia"));
        mvc.perform(post("/api/v1/allocation-undo-requests/3/approve").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_NOT_ALLOWED"));
    }

    @Test
    void approve_alreadyDecided_409_UNDO_REQUEST_ALREADY_DECIDED() throws Exception {
        when(undo.approve(3L, null)).thenThrow(new UndoRequestAlreadyDecidedException("resuelta"));
        mvc.perform(post("/api/v1/allocation-undo-requests/3/approve").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNDO_REQUEST_ALREADY_DECIDED"));
    }

    @Test
    void approve_notFound_404() throws Exception {
        when(undo.approve(3L, null)).thenThrow(new UndoRequestNotFoundException(3L));
        mvc.perform(post("/api/v1/allocation-undo-requests/3/approve").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reject_200() throws Exception {
        when(undo.reject(3L, "no procede")).thenReturn(decided(UndoRequestStatus.REJECTED));
        mvc.perform(post("/api/v1/allocation-undo-requests/3/reject").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"no procede\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void reject_blankNote_422() throws Exception {
        mvc.perform(post("/api/v1/allocation-undo-requests/3/reject").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(undo);
    }

    @Test
    void domainIllegalArgument_422_VALIDATION_ERROR() throws Exception {
        when(undo.approve(any(), any())).thenThrow(new IllegalArgumentException("x"));
        mvc.perform(post("/api/v1/allocation-undo-requests/3/approve").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
