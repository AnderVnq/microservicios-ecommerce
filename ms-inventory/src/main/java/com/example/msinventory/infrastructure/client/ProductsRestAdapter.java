package com.example.msinventory.infrastructure.client;

import com.example.msinventory.application.port.out.ProductsPort;
import com.example.msinventory.domain.exception.ProductsUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
class ProductsRestAdapter implements ProductsPort {

    private final RestClient restClient;

    ProductsRestAdapter(@Qualifier("productsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<String> canonicalSku(String sku) {
        ExistsResponse body;
        try {
            body = restClient.post()
                    .uri("/api/v1/variants/exists")
                    .body(new ExistsRequest(List.of(sku)))
                    .retrieve()
                    .body(ExistsResponse.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            // 5xx, 401, 403, 408 y 429 son indisponibilidad; cualquier otro 4xx es un bug del cliente.
            if (!e.getStatusCode().is4xxClientError() || status == 401 || status == 403 || status == 408
                    || status == 429) {
                throw new ProductsUnavailableException("ms-products respondio " + e.getStatusCode());
            }
            throw new IllegalStateException("ms-products rechazo la peticion: " + e.getStatusCode());
        } catch (RestClientException e) {
            throw new ProductsUnavailableException("ms-products no disponible: " + e.getMessage());
        }
        if (body == null) {
            throw new ProductsUnavailableException("ms-products respondio sin cuerpo");
        }
        return body.canonical() == null ? Optional.empty() : Optional.ofNullable(body.canonical().get(sku));
    }

    record ExistsRequest(List<String> skus) {
    }

    /** existing se conserva en ms-products por compatibilidad; aqui solo se usa canonical. */
    record ExistsResponse(List<String> existing, Map<String, String> canonical) {
    }
}
