package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.AllocationUndoUseCase;
import com.example.mspedidos.domain.model.UndoRequestStatus;
import com.example.mspedidos.infrastructure.web.dto.AllocationUndoRequestResponse;
import com.example.mspedidos.infrastructure.web.dto.ApproveUndoRequest;
import com.example.mspedidos.infrastructure.web.dto.RejectUndoRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador driver: solicitudes para deshacer la cobertura de inventario. */
@RestController
@RequestMapping("/api/v1/allocation-undo-requests")
@RequiredArgsConstructor
public class AllocationUndoRequestController {

    private final AllocationUndoUseCase undoUseCase;

    @GetMapping
    public List<AllocationUndoRequestResponse> search(@RequestParam(required = false) UndoRequestStatus status) {
        return undoUseCase.search(status).stream().map(AllocationUndoRequestResponse::from).toList();
    }

    @PostMapping("/{id}/approve")
    public AllocationUndoRequestResponse approve(@PathVariable Long id,
                                                 @RequestBody(required = false) ApproveUndoRequest request) {
        return AllocationUndoRequestResponse.from(undoUseCase.approve(id, request == null ? null : request.note()));
    }

    @PostMapping("/{id}/reject")
    public AllocationUndoRequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectUndoRequest request) {
        return AllocationUndoRequestResponse.from(undoUseCase.reject(id, request.note()));
    }
}
