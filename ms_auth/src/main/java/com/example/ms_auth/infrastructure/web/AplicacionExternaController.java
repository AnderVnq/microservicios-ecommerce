package com.example.ms_auth.infrastructure.web;

import com.example.ms_auth.application.port.in.AplicacionExternaUseCase;
import com.example.ms_auth.infrastructure.web.dto.AplicacionExternaRequest;
import com.example.ms_auth.infrastructure.web.dto.AplicacionExternaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aplicaciones-externas")
@RequiredArgsConstructor
public class AplicacionExternaController {

    private final AplicacionExternaUseCase aplicacionExternaUseCase;

    @GetMapping
    public List<AplicacionExternaResponse> findAll() {
        return aplicacionExternaUseCase.findAll().stream().map(AplicacionExternaResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AplicacionExternaResponse findById(@PathVariable Long id) {
        return AplicacionExternaResponse.from(aplicacionExternaUseCase.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AplicacionExternaResponse create(@Valid @RequestBody AplicacionExternaRequest request) {
        return AplicacionExternaResponse.from(aplicacionExternaUseCase.create(
                request.keycloakClientId(), request.nombre(), request.empresa()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        aplicacionExternaUseCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/permisos/{permisoId}")
    public AplicacionExternaResponse asignarPermiso(@PathVariable Long id, @PathVariable Long permisoId) {
        return AplicacionExternaResponse.from(aplicacionExternaUseCase.asignarPermiso(id, permisoId));
    }

    @DeleteMapping("/{id}/permisos/{permisoId}")
    public AplicacionExternaResponse quitarPermiso(@PathVariable Long id, @PathVariable Long permisoId) {
        return AplicacionExternaResponse.from(aplicacionExternaUseCase.quitarPermiso(id, permisoId));
    }
}
