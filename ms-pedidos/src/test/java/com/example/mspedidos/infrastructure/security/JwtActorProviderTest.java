package com.example.mspedidos.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtActorProviderTest {

    private final JwtActorProvider provider = new JwtActorProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(Map<String, Object> claims) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void usesSubWhenPresent() {
        authenticate(Map.of("sub", "user-1", "azp", "client-1"));
        assertThat(provider.currentActor()).isEqualTo("user-1");
    }

    @Test
    void fallsBackToAzp() {
        authenticate(Map.of("azp", "client-1"));
        assertThat(provider.currentActor()).isEqualTo("client-1");
    }

    @Test
    void fallsBackToSystem() {
        authenticate(Map.of("scope", "x"));
        assertThat(provider.currentActor()).isEqualTo("system");
        SecurityContextHolder.clearContext();
        assertThat(provider.currentActor()).isEqualTo("system");
    }
}
