package com.example.msproducts.infrastructure.client;

import com.example.msproducts.application.port.out.MarketsPort;
import com.example.msproducts.domain.exception.MarketsUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Consulta a ms-markets reenviando el token del usuario: 404 = no existe; cualquier otra falla = no disponible. */
@Component
class MarketsRestAdapter implements MarketsPort {

    private final RestClient restClient;

    MarketsRestAdapter(@Qualifier("marketsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean exists(Long marketId) {
        try {
            restClient.get().uri("/api/v1/markets/{id}", marketId).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return false;
            }
            throw new MarketsUnavailableException("ms-markets respondio " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new MarketsUnavailableException("ms-markets no disponible", e);
        }
    }
}
