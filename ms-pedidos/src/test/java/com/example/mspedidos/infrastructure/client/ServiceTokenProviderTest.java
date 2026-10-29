package com.example.mspedidos.infrastructure.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceTokenProviderTest {

    private static final String URI = "http://kc/token";

    private MockRestServiceServer server;
    private ServiceTokenProvider provider;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        provider = new ServiceTokenProvider(builder.build(), URI, "ms-pedidos", "sec", clock);
    }

    private static String body(String token, int expiresIn) {
        return "{\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + ",\"token_type\":\"Bearer\"}";
    }

    @Test
    void token_postsClientCredentialsForm() {
        server.expect(once(), requestTo(URI)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(new org.springframework.util.LinkedMultiValueMap<>(java.util.Map.of(
                        "grant_type", java.util.List.of("client_credentials"),
                        "client_id", java.util.List.of("ms-pedidos"),
                        "client_secret", java.util.List.of("sec")))))
                .andRespond(withSuccess(body("t1", 300), MediaType.APPLICATION_JSON));

        assertThat(provider.token()).isEqualTo("t1");
        server.verify();
    }

    @Test
    void token_cachedUntilNearExpiry() {
        server.expect(once(), requestTo(URI)).andRespond(withSuccess(body("t1", 300), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(URI)).andRespond(withSuccess(body("t2", 300), MediaType.APPLICATION_JSON));

        assertThat(provider.token()).isEqualTo("t1");
        now.set(now.get().plus(Duration.ofSeconds(269)));
        assertThat(provider.token()).isEqualTo("t1");   // sin HTTP: faltan 31 s
        now.set(now.get().plus(Duration.ofSeconds(1)));  // faltan 30 s -> renovar
        assertThat(provider.token()).isEqualTo("t2");
        server.verify();
    }

    @Test
    void token_failure_throwsServiceTokenException() {
        server.expect(once(), requestTo(URI)).andRespond(withServerError());

        assertThatThrownBy(provider::token).isInstanceOf(ServiceTokenException.class);
    }

    @Test
    void token_missingAccessToken_throwsServiceTokenException() {
        server.expect(once(), requestTo(URI)).andRespond(withSuccess("{\"expires_in\":300}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(provider::token).isInstanceOf(ServiceTokenException.class);
    }
}
