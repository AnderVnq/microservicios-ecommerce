package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.domain.exception.CupoImportacionNoDisponibleException;
import com.example.mspedidos.application.port.out.CupoImportacionPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Adaptador driven: llama al endpoint de cupo de ms-clientes para
 * reservar/liberar una unidad de importaciones del cliente titular.
 *
 * ms-clientes tambien exige un JWT valido, asi que esta llamada interna
 * reenvia el Authorization de la request entrante en vez de usar
 * credenciales propias (token relay simple, sin un client-credentials
 * dedicado para llamadas servicio-a-servicio).
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
                    .headers(this::forwardAuthorization)
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
                .headers(this::forwardAuthorization)
                .retrieve()
                .toBodilessEntity();
    }

    private void forwardAuthorization(HttpHeaders headers) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
    }
}
