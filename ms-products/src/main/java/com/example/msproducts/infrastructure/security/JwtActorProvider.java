package com.example.msproducts.infrastructure.security;

import com.example.msproducts.application.port.out.ActorProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Actor de auditoria: {@code sub} del JWT, si no {@code azp}, si no {@code "system"}. */
@Component
class JwtActorProvider implements ActorProvider {

    static final String SYSTEM = "system";

    @Override
    public String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return SYSTEM;
        }
        if (hasText(jwt.getSubject())) {
            return jwt.getSubject();
        }
        String azp = jwt.getClaimAsString("azp");
        return hasText(azp) ? azp : SYSTEM;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
