package com.example.msinventory.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Valida el JWT de forma independiente al gateway (defensa en profundidad):
 * este servicio nunca confia ciegamente en que una request ya paso por el
 * gateway, por si llega directo o el gateway esta mal configurado.
 *
 * <p>Asignar/liberar unidades es una operacion interna de ms-pedidos: solo se acepta un JWT cuyo
 * {@code azp} sea el client id de servicio configurado; cualquier otro token del realm recibe 403.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ALLOCATION_PATH = "/api/v1/inventory/units/*/allocation";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    @Value("${inventory.allocation.allowed-client-id:ms-pedidos}") String allowedClientId)
            throws Exception {
        AuthorizationManager<RequestAuthorizationContext> serviceClientOnly = serviceClientOnly(allowedClientId);
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.POST, ALLOCATION_PATH).access(serviceClientOnly)
                        .requestMatchers(HttpMethod.DELETE, ALLOCATION_PATH).access(serviceClientOnly)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }

    private static AuthorizationManager<RequestAuthorizationContext> serviceClientOnly(String allowedClientId) {
        return (authentication, context) -> {
            var auth = authentication.get();
            boolean granted = auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt
                    && allowedClientId.equals(jwt.getClaimAsString("azp"));
            return new AuthorizationDecision(granted);
        };
    }
}
