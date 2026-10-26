package com.example.msclientes.infrastructure.web;

import com.example.msclientes.application.port.in.FamiliarUseCase;
import com.example.msclientes.infrastructure.web.dto.FamiliarRequest;
import com.example.msclientes.infrastructure.web.dto.FamiliarResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clientes/{clienteId}/familiares")
@RequiredArgsConstructor
public class FamiliarController {

    private final FamiliarUseCase familiarUseCase;

    @GetMapping
    public List<FamiliarResponse> findAll(@PathVariable Long clienteId) {
        return familiarUseCase.findByCliente(clienteId).stream().map(FamiliarResponse::from).toList();
    }

    @GetMapping("/{familiarId}")
    public FamiliarResponse findOne(@PathVariable Long clienteId, @PathVariable Long familiarId) {
        return FamiliarResponse.from(familiarUseCase.findOne(clienteId, familiarId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamiliarResponse create(@PathVariable Long clienteId, @Valid @RequestBody FamiliarRequest request) {
        return FamiliarResponse.from(familiarUseCase.create(clienteId, request.nombres(), request.apellidos(),
                request.tipoDocumento(), request.numeroDocumento(), request.parentesco()));
    }

    @DeleteMapping("/{familiarId}")
    public ResponseEntity<Void> delete(@PathVariable Long clienteId, @PathVariable Long familiarId) {
        familiarUseCase.deactivate(clienteId, familiarId);
        return ResponseEntity.noContent().build();
    }
}
