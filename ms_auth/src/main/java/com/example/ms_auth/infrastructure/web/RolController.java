package com.example.ms_auth.infrastructure.web;

import com.example.ms_auth.application.port.in.RolUseCase;
import com.example.ms_auth.infrastructure.web.dto.RolRequest;
import com.example.ms_auth.infrastructure.web.dto.RolResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RolController {

    private final RolUseCase rolUseCase;

    @GetMapping
    public List<RolResponse> findAll() {
        return rolUseCase.findAll().stream().map(RolResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RolResponse findById(@PathVariable Long id) {
        return RolResponse.from(rolUseCase.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RolResponse create(@Valid @RequestBody RolRequest request) {
        return RolResponse.from(rolUseCase.create(request.nombre(), request.descripcion()));
    }

    @PostMapping("/{id}/permisos/{permisoId}")
    public RolResponse asignarPermiso(@PathVariable Long id, @PathVariable Long permisoId) {
        return RolResponse.from(rolUseCase.asignarPermiso(id, permisoId));
    }

    @DeleteMapping("/{id}/permisos/{permisoId}")
    public RolResponse quitarPermiso(@PathVariable Long id, @PathVariable Long permisoId) {
        return RolResponse.from(rolUseCase.quitarPermiso(id, permisoId));
    }
}
