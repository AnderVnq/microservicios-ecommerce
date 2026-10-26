package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.domain.exception.CupoImportacionNoDisponibleException;
import com.example.mspedidos.application.port.out.CupoImportacionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Adaptador driven: llama al endpoint de cupo de ms-clientes para
 * reservar/liberar una unidad de importaciones del cliente titular.
 */
@Component
class ClientesCupoAdapter implements CupoImportacionPort {

    private final RestClient restClient;

    ClientesCupoAdapter(@Value("${clientes-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public void reservar(Long clienteId, Long pedidoId, String idempotencyKey) {
        try {
            restClient.post()
                    .uri("/api/v1/clientes/{clienteId}/cupo/reservas", clienteId)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(Map.of("pedidoId", pedidoId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new CupoImportacionNoDisponibleException(clienteId);
        }
    }

    @Override
    public void liberar(Long clienteId, String idempotencyKey) {
        restClient.delete()
                .uri("/api/v1/clientes/{clienteId}/cupo/reservas/{idempotencyKey}", clienteId, idempotencyKey)
                .retrieve()
                .toBodilessEntity();
    }
}
