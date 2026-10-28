package com.example.ms_auth.infrastructure.web;

import com.example.ms_auth.application.port.in.UsuarioUseCase;
import com.example.ms_auth.infrastructure.web.dto.UsuarioRequest;
import com.example.ms_auth.infrastructure.web.dto.UsuarioResponse;
import com.example.ms_auth.infrastructure.web.dto.UsuarioUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioUseCase usuarioUseCase;

    @GetMapping
    public List<UsuarioResponse> findAll() {
        return usuarioUseCase.findAll().stream().map(UsuarioResponse::from).toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse findById(@PathVariable Long id) {
        return UsuarioResponse.from(usuarioUseCase.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse create(@Valid @RequestBody UsuarioRequest request) {
        return UsuarioResponse.from(usuarioUseCase.create(
                request.keycloakUserId(), request.nombres(), request.apellidos(), request.email()));
    }

    @PutMapping("/{id}")
    public UsuarioResponse update(@PathVariable Long id, @Valid @RequestBody UsuarioUpdateRequest request) {
        return UsuarioResponse.from(usuarioUseCase.update(id, request.nombres(), request.apellidos(), request.email()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        usuarioUseCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/roles/{rolId}")
    public UsuarioResponse asignarRol(@PathVariable Long id, @PathVariable Long rolId) {
        return UsuarioResponse.from(usuarioUseCase.asignarRol(id, rolId));
    }

    @DeleteMapping("/{id}/roles/{rolId}")
    public UsuarioResponse quitarRol(@PathVariable Long id, @PathVariable Long rolId) {
        return UsuarioResponse.from(usuarioUseCase.quitarRol(id, rolId));
    }
}
