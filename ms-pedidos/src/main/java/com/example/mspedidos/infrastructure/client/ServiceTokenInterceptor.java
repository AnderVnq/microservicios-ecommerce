package com.example.mspedidos.infrastructure.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/** Autentica las llamadas internas con el token de servicio (no reenvia el token del usuario). */
class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    private final ServiceTokenProvider tokens;

    ServiceTokenInterceptor(ServiceTokenProvider tokens) {
        this.tokens = tokens;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
        return execution.execute(request, body);
    }
}
