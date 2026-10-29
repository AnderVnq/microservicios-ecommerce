package com.example.mspedidos.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Obtiene (y cachea) el token de la cuenta de servicio de ms-pedidos via client_credentials. */
class ServiceTokenProvider {

    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String cachedToken;
    private Instant refreshAt = Instant.MIN;

    ServiceTokenProvider(RestClient restClient, String tokenUri, String clientId, String clientSecret, Clock clock) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    synchronized String token() {
        Instant now = clock.instant();
        if (cachedToken == null || !now.isBefore(refreshAt)) {
            refresh(now);
        }
        return cachedToken;
    }

    private void refresh(Instant now) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            throw new ServiceTokenException("No se pudo obtener el token de servicio", e);
        }
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new ServiceTokenException("Respuesta de token de servicio sin access_token", null);
        }
        cachedToken = response.accessToken();
        refreshAt = now.plusSeconds(response.expiresIn()).minus(SAFETY_MARGIN);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {
    }
}
