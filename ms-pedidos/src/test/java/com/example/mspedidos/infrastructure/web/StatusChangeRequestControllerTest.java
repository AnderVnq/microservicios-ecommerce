package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.StatusChangeRequestUseCase;
import com.example.mspedidos.domain.exception.ApprovalForbiddenException;
import com.example.mspedidos.domain.exception.AuthUnavailableException;
import com.example.mspedidos.domain.exception.SelfApprovalNotAllowedException;
import com.example.mspedidos.domain.exception.StatusChangeRequestAlreadyDecidedException;
import com.example.mspedidos.domain.exception.StatusChangeRequestNotFoundException;
import com.example.mspedidos.domain.exception.UndoNotAllowedException;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
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

@WebMvcTest(StatusChangeRequestController.class)
@Import(SecurityConfig.class)
class StatusChangeRequestControllerTest {

    private static final String BASE = "/api/v1/status-change-requests";

    @Autowired MockMvc mvc;
    @MockitoBean StatusChangeRequestUseCase requests;
    @MockitoBean JwtDecoder jwtDecoder;

    private static StatusChangeRequest decided(StatusChangeRequestStatus status) {
        return StatusChangeRequest.builder().id(3L).type(StatusChangeRequestType.ALLOCATION_UNDO).orderItemId(1L)
                .inventoryUnitId(99L).reason("mal cubierta").status(status).requestedBy("u1").decidedBy("admin")
                .decisionNote("ok").requestedAt(Instant.parse("2026-03-01T10:00:00Z"))
                .decidedAt(Instant.parse("2026-03-02T10:00:00Z")).build();
    }

    @Test
    void search_withStatus_mapsAllFields() throws Exception {
        when(requests.search(StatusChangeRequestStatus.APPROVED, null))
                .thenReturn(List.of(decided(StatusChangeRequestStatus.APPROVED)));

        mvc.perform(get(BASE).param("status", "APPROVED").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].type").value("ALLOCATION_UNDO"))
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
    void search_notReceived_mapsPurchaseFields() throws Exception {
        StatusChangeRequest notReceived = StatusChangeRequest.builder().id(4L)
                .type(StatusChangeRequestType.PURCHASE_NOT_RECEIVED).purchaseId(12L).quantity(2).reason("no llego")
                .status(StatusChangeRequestStatus.PENDING).requestedBy("u1")
                .requestedAt(Instant.parse("2026-03-01T10:00:00Z")).build();
        when(requests.search(null, StatusChangeRequestType.PURCHASE_NOT_RECEIVED)).thenReturn(List.of(notReceived));

        mvc.perform(get(BASE).param("type", "PURCHASE_NOT_RECEIVED").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("PURCHASE_NOT_RECEIVED"))
                .andExpect(jsonPath("$[0].purchaseId").value(12))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].orderItemId").isEmpty());
    }

    @Test
    void search_withoutFilters_passesNulls() throws Exception {
        when(requests.search(null, null)).thenReturn(List.of());
        mvc.perform(get(BASE).with(jwt())).andExpect(status().isOk());
        verify(requests).search(null, null);
    }

    @Test
    void search_statusAndType_passed() throws Exception {
        when(requests.search(StatusChangeRequestStatus.PENDING, StatusChangeRequestType.ALLOCATION_UNDO))
                .thenReturn(List.of());
        mvc.perform(get(BASE).param("status", "PENDING").param("type", "ALLOCATION_UNDO").with(jwt()))
                .andExpect(status().isOk());
        verify(requests).search(StatusChangeRequestStatus.PENDING, StatusChangeRequestType.ALLOCATION_UNDO);
    }

    @Test
    void search_unknownStatus_422() throws Exception {
        mvc.perform(get(BASE).param("status", "NOPE").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void search_unknownType_422() throws Exception {
        mvc.perform(get(BASE).param("type", "NOPE").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void search_withoutToken_401() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(requests);
    }

    @Test
    void approve_200_withNote() throws Exception {
        when(requests.approve(3L, "ok")).thenReturn(decided(StatusChangeRequestStatus.APPROVED));
        mvc.perform(post(BASE + "/3/approve").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approve_noBody_200_nullNote() throws Exception {
        when(requests.approve(3L, null)).thenReturn(decided(StatusChangeRequestStatus.APPROVED));
        mvc.perform(post(BASE + "/3/approve").with(jwt()))
                .andExpect(status().isOk());
        verify(requests).approve(3L, null);
    }

    @Test
    void approve_blankNote_allowed() throws Exception {
        when(requests.approve(3L, " ")).thenReturn(decided(StatusChangeRequestStatus.APPROVED));
        mvc.perform(post(BASE + "/3/approve").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\" \"}"))
                .andExpect(status().isOk());
    }

    @Test
    void approve_forbidden_403() throws Exception {
        when(requests.approve(3L, null)).thenThrow(new ApprovalForbiddenException("sin permiso"));
        mvc.perform(post(BASE + "/3/approve").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_FORBIDDEN"));
    }

    @Test
    void approve_authDown_503() throws Exception {
        when(requests.approve(3L, null)).thenThrow(new AuthUnavailableException("caido"));
        mvc.perform(post(BASE + "/3/approve").with(jwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
    }

    @Test
    void approve_selfApproval_409() throws Exception {
        when(requests.approve(3L, null)).thenThrow(new SelfApprovalNotAllowedException("propia"));
        mvc.perform(post(BASE + "/3/approve").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_NOT_ALLOWED"));
    }

    @Test
    void approve_alreadyDecided_409_STATUS_CHANGE_REQUEST_ALREADY_DECIDED() throws Exception {
        when(requests.approve(3L, null)).thenThrow(new StatusChangeRequestAlreadyDecidedException("resuelta"));
        mvc.perform(post(BASE + "/3/approve").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATUS_CHANGE_REQUEST_ALREADY_DECIDED"));
    }

    @Test
    void approve_undoNoLongerPossible_409_UNDO_NOT_ALLOWED() throws Exception {
        when(requests.approve(3L, null)).thenThrow(new UndoNotAllowedException("ya avanzo"));
        mvc.perform(post(BASE + "/3/approve").with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNDO_NOT_ALLOWED"));
    }

    @Test
    void approve_notFound_404() throws Exception {
        when(requests.approve(3L, null)).thenThrow(new StatusChangeRequestNotFoundException(3L));
        mvc.perform(post(BASE + "/3/approve").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void reject_200() throws Exception {
        when(requests.reject(3L, "no procede")).thenReturn(decided(StatusChangeRequestStatus.REJECTED));
        mvc.perform(post(BASE + "/3/reject").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"no procede\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void reject_blankNote_422() throws Exception {
        mvc.perform(post(BASE + "/3/reject").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(requests);
    }

    @Test
    void reject_missingNote_422() throws Exception {
        mvc.perform(post(BASE + "/3/reject").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(requests);
    }

    @Test
    void domainIllegalArgument_422_VALIDATION_ERROR() throws Exception {
        when(requests.approve(any(), any())).thenThrow(new IllegalArgumentException("x"));
        mvc.perform(post(BASE + "/3/approve").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void oldAllocationUndoListPath_404() throws Exception {
        mvc.perform(get("/api/v1/allocation-undo-requests").with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/allocation-undo-requests/3/approve").with(jwt()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(requests);
    }
}
