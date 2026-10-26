package com.example.msclientes.infrastructure.web;

import com.example.msclientes.application.port.in.CupoImportacionUseCase;
import com.example.msclientes.infrastructure.web.dto.CupoResponse;
import com.example.msclientes.infrastructure.web.dto.ReservaCupoRequest;
import com.example.msclientes.infrastructure.web.dto.ReservaCupoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Consumido por ms-pedidos antes/despues de confirmar o cancelar un pedido
 * que requiere importacion. El header Idempotency-Key es obligatorio en la
 * reserva para que los reintentos del llamador nunca dupliquen el consumo.
 */
@RestController
@RequestMapping("/api/v1/clientes/{clienteId}/cupo")
@RequiredArgsConstructor
public class CupoImportacionController {

    private final CupoImportacionUseCase cupoImportacionUseCase;

    @GetMapping
    public CupoResponse consultar(@PathVariable Long clienteId) {
        return CupoResponse.from(cupoImportacionUseCase.consultarCupo(clienteId));
    }

    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaCupoResponse reservar(@PathVariable Long clienteId,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         @Valid @RequestBody ReservaCupoRequest request) {
        return ReservaCupoResponse.from(
                cupoImportacionUseCase.reservar(clienteId, request.pedidoId(), idempotencyKey));
    }

    @DeleteMapping("/reservas/{idempotencyKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void liberar(@PathVariable Long clienteId, @PathVariable String idempotencyKey) {
        cupoImportacionUseCase.liberar(idempotencyKey);
    }
}
