package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.PedidoUseCase;
import com.example.mspedidos.infrastructure.web.dto.CambiarEstadoRequest;
import com.example.mspedidos.infrastructure.web.dto.PedidoRequest;
import com.example.mspedidos.infrastructure.web.dto.PedidoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoUseCase pedidoUseCase;

    @GetMapping
    public List<PedidoResponse> findAll() {
        return pedidoUseCase.findAll().stream().map(PedidoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PedidoResponse findById(@PathVariable Long id) {
        return PedidoResponse.from(pedidoUseCase.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse create(@Valid @RequestBody PedidoRequest request) {
        return PedidoResponse.from(pedidoUseCase.crear(
                request.marketId(), request.clienteId(), request.familiarId(),
                request.requiereImportacion(), request.total(), request.moneda()));
    }

    @PatchMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return PedidoResponse.from(pedidoUseCase.cambiarEstado(id, request.estado()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        pedidoUseCase.cancelar(id);
        return ResponseEntity.noContent().build();
    }
}
