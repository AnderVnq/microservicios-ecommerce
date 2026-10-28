package com.example.ms_auth.infrastructure.web;

import com.example.ms_auth.application.port.in.PermisoUseCase;
import com.example.ms_auth.infrastructure.web.dto.PermisoRequest;
import com.example.ms_auth.infrastructure.web.dto.PermisoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permisos")
@RequiredArgsConstructor
public class PermisoController {

    private final PermisoUseCase permisoUseCase;

    @GetMapping
    public List<PermisoResponse> findAll() {
        return permisoUseCase.findAll().stream().map(PermisoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PermisoResponse findById(@PathVariable Long id) {
        return PermisoResponse.from(permisoUseCase.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PermisoResponse create(@Valid @RequestBody PermisoRequest request) {
        return PermisoResponse.from(permisoUseCase.create(request.recurso(), request.accion(), request.descripcion()));
    }
}
