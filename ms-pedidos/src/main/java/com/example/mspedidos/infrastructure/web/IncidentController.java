package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.IncidentUseCase;
import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.infrastructure.web.dto.IncidentResponse;
import com.example.mspedidos.infrastructure.web.dto.OpenIncidentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador driver: incidencias de unidades. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentUseCase incidentUseCase;

    @GetMapping("/incidents")
    public List<IncidentResponse> search(@RequestParam(required = false) IncidentStatus status,
                                         @RequestParam(required = false) IncidentType type,
                                         @RequestParam(required = false) IncidentScope scope) {
        return incidentUseCase.search(status, type, scope).stream().map(IncidentResponse::from).toList();
    }

    @PostMapping("/orders/{orderId}/items/{itemId}/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse open(@PathVariable Long orderId, @PathVariable Long itemId,
                                 @Valid @RequestBody OpenIncidentRequest request) {
        return IncidentResponse.from(incidentUseCase.open(
                orderId, itemId, request.type(), request.scope(), request.description()));
    }

    @PostMapping("/incidents/{id}/resolve")
    public IncidentResponse resolve(@PathVariable Long id) {
        return IncidentResponse.from(incidentUseCase.resolve(id));
    }
}
