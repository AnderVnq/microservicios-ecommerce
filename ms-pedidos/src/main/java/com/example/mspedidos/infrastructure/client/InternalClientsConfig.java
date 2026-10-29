package com.example.mspedidos.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/** Clientes internos (ms-inventory, ms_auth) autenticados con el token de la cuenta de servicio. */
@Configuration
class InternalClientsConfig {

    @Bean
    ServiceTokenProvider serviceTokenProvider(@Value("${service-account.token-uri}") String tokenUri,
                                              @Value("${service-account.client-id}") String clientId,
                                              @Value("${service-account.client-secret}") String clientSecret) {
        RestClient restClient = RestClient.builder().requestFactory(requestFactory()).build();
        return new ServiceTokenProvider(restClient, tokenUri, clientId, clientSecret, Clock.systemUTC());
    }

    @Bean
    RestClient inventoryRestClient(@Value("${inventory.base-url}") String baseUrl, ServiceTokenProvider tokens) {
        return serviceClient(baseUrl, tokens);
    }

    @Bean
    RestClient authRestClient(@Value("${auth.base-url}") String baseUrl, ServiceTokenProvider tokens) {
        return serviceClient(baseUrl, tokens);
    }

    private static RestClient serviceClient(String baseUrl, ServiceTokenProvider tokens) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .requestInterceptor(new ServiceTokenInterceptor(tokens))
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return requestFactory;
    }
}
