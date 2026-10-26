package com.example.msclientes.infrastructure.web;

import com.example.msclientes.application.port.in.ClienteUseCase;
import com.example.msclientes.infrastructure.web.dto.ClienteRequest;
import com.example.msclientes.infrastructure.web.dto.ClienteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteUseCase clienteUseCase;

    @GetMapping
    public List<ClienteResponse> findAll() {
        return clienteUseCase.findAll().stream().map(ClienteResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ClienteResponse findById(@PathVariable Long id) {
        return ClienteResponse.from(clienteUseCase.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse create(@Valid @RequestBody ClienteRequest request) {
        return ClienteResponse.from(clienteUseCase.create(
                request.nombres(), request.apellidos(), request.tipoDocumento(),
                request.numeroDocumento(), request.email(), request.telefono()));
    }

    @PutMapping("/{id}")
    public ClienteResponse update(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return ClienteResponse.from(clienteUseCase.update(id, request.nombres(), request.apellidos(),
                request.email(), request.telefono()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        clienteUseCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
