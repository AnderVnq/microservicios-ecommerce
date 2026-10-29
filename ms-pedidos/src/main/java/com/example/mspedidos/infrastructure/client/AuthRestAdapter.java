package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.application.port.out.AuthorizationPort;
import com.example.mspedidos.domain.exception.AuthUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class AuthRestAdapter implements AuthorizationPort {

    private final RestClient restClient;

    AuthRestAdapter(@Qualifier("authRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean isAllowed(String subject, String resource, String action) {
        AuthorizationResponse body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/autorizacion")
                            .queryParam("subject", subject)
                            .queryParam("recurso", resource)
                            .queryParam("accion", action)
                            .build())
                    .retrieve()
                    .body(AuthorizationResponse.class);
        } catch (ServiceTokenException e) {
            throw new AuthUnavailableException("No se pudo obtener el token de servicio para ms_auth");
        } catch (RestClientException e) {
            throw new AuthUnavailableException("ms_auth no disponible: " + e.getMessage());
        }
        if (body == null || body.permitido() == null) {
            throw new AuthUnavailableException("ms_auth respondio sin decision");
        }
        return body.permitido();
    }

    record AuthorizationResponse(String subject, String recurso, String accion, Boolean permitido) {
    }
}
