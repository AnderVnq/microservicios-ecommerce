package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.StatusChangeRequestUseCase;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import com.example.mspedidos.infrastructure.web.dto.DecisionRequest;
import com.example.mspedidos.infrastructure.web.dto.StatusChangeRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador driver: listar, aprobar y rechazar solicitudes de cambio de estado (spec 8.4). */
@RestController
@RequestMapping("/api/v1/status-change-requests")
@RequiredArgsConstructor
public class StatusChangeRequestController {

    private final StatusChangeRequestUseCase requests;

    @GetMapping
    public List<StatusChangeRequestResponse> search(@RequestParam(required = false) StatusChangeRequestStatus status,
                                                    @RequestParam(required = false) StatusChangeRequestType type) {
        return requests.search(status, type).stream().map(StatusChangeRequestResponse::from).toList();
    }

    @PostMapping("/{id}/approve")
    public StatusChangeRequestResponse approve(@PathVariable Long id,
                                               @RequestBody(required = false) DecisionRequest request) {
        return StatusChangeRequestResponse.from(requests.approve(id, request == null ? null : request.note()));
    }

    @PostMapping("/{id}/reject")
    public StatusChangeRequestResponse reject(@PathVariable Long id,
                                              @Validated(DecisionRequest.Reject.class) @RequestBody DecisionRequest request) {
        return StatusChangeRequestResponse.from(requests.reject(id, request.note()));
    }
}
