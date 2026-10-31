package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.application.port.out.ProductsPort;
import com.example.mspedidos.domain.exception.ProductsUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
class ProductsRestAdapter implements ProductsPort {

    private static final int CHUNK = 500;

    private final RestClient restClient;

    ProductsRestAdapter(@Qualifier("productsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Map<String, String> resolve(Long marketId, Collection<String> marketSkus) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (List<String> chunk : chunks(marketSkus)) {
            List<MarketSkuRef> refs = chunk.stream().map(s -> new MarketSkuRef(marketId, s)).toList();
            ResolvedEntry[] body = call(() -> restClient.post()
                    .uri("/api/v1/variants/resolve")
                    .body(new ResolveRequest(refs))
                    .retrieve()
                    .body(ResolvedEntry[].class));
            if (body == null) {
                throw new ProductsUnavailableException("ms-products respondio sin cuerpo");
            }
            for (ResolvedEntry entry : body) {
                if (entry != null && entry.marketSku() != null && entry.sku() != null) {
                    resolved.put(entry.marketSku(), entry.sku());
                }
            }
        }
        return resolved;
    }

    @Override
    public Map<String, String> canonical(Collection<String> skus) {
        Map<String, String> canonical = new LinkedHashMap<>();
        for (List<String> chunk : chunks(skus)) {
            ExistsResponse body = call(() -> restClient.post()
                    .uri("/api/v1/variants/exists")
                    .body(new ExistsRequest(chunk))
                    .retrieve()
                    .body(ExistsResponse.class));
            if (body == null) {
                throw new ProductsUnavailableException("ms-products respondio sin cuerpo");
            }
            if (body.canonical() != null) {
                body.canonical().forEach((input, stored) -> {
                    if (input != null && stored != null) {
                        canonical.put(input, stored);
                    }
                });
            }
        }
        return canonical;
    }

    private static List<List<String>> chunks(Collection<String> values) {
        List<String> distinct = new ArrayList<>(new LinkedHashSet<>(values));
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < distinct.size(); i += CHUNK) {
            chunks.add(distinct.subList(i, Math.min(i + CHUNK, distinct.size())));
        }
        return chunks;
    }

    /** 5xx, 401, 403, 408, 429 e IO son indisponibilidad; cualquier otro 4xx es un bug del cliente. */
    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (!e.getStatusCode().is4xxClientError() || status == 401 || status == 403 || status == 408
                    || status == 429) {
                throw new ProductsUnavailableException("ms-products respondio " + e.getStatusCode());
            }
            throw new IllegalStateException("ms-products rechazo la peticion: " + e.getStatusCode());
        } catch (ServiceTokenException e) {
            throw new ProductsUnavailableException("No se pudo obtener el token de servicio para ms-products");
        } catch (RestClientException e) {
            throw new ProductsUnavailableException("ms-products no disponible: " + e.getMessage());
        }
    }

    record MarketSkuRef(Long marketId, String marketSku) {
    }

    record ResolveRequest(List<MarketSkuRef> marketSkus) {
    }

    record ResolvedEntry(Long marketId, String marketSku, String sku) {
    }

    record ExistsRequest(List<String> skus) {
    }

    /** existing se conserva en ms-products por compatibilidad; aqui solo se usa canonical. */
    record ExistsResponse(List<String> existing, Map<String, String> canonical) {
    }
}
