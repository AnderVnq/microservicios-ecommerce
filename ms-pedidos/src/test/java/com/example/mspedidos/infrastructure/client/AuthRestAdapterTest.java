package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.domain.exception.AuthUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthRestAdapterTest {

    private static final String BASE = "http://auth";
    private static final String URL = BASE + "/api/v1/autorizacion?subject=u1&recurso=pedidos&accion=aprobar";

    private MockRestServiceServer server;
    private AuthRestAdapter adapter;
    private ServiceTokenProvider tokens;

    @BeforeEach
    void setUp() {
        tokens = mock(ServiceTokenProvider.class);
        when(tokens.token()).thenReturn("svc-token");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE)
                .requestInterceptor(new ServiceTokenInterceptor(tokens));
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new AuthRestAdapter(builder.build());
    }

    private static String json(boolean permitido) {
        return "{\"subject\":\"u1\",\"recurso\":\"pedidos\",\"accion\":\"aprobar\",\"permitido\":" + permitido + "}";
    }

    @Test
    void isAllowed_true() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer svc-token"))
                .andRespond(withSuccess(json(true), MediaType.APPLICATION_JSON));

        assertThat(adapter.isAllowed("u1", "pedidos", "aprobar")).isTrue();
        server.verify();
    }

    @Test
    void isAllowed_false() {
        server.expect(requestTo(URL)).andRespond(withSuccess(json(false), MediaType.APPLICATION_JSON));

        assertThat(adapter.isAllowed("u1", "pedidos", "aprobar")).isFalse();
    }

    @Test
    void isAllowed_error_failsClosed() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.isAllowed("u1", "pedidos", "aprobar"))
                .isInstanceOf(AuthUnavailableException.class);
    }

    @Test
    void isAllowed_403_failsClosed() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adapter.isAllowed("u1", "pedidos", "aprobar"))
                .isInstanceOf(AuthUnavailableException.class);
    }

    @Test
    void isAllowed_emptyBody_failsClosed() {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> adapter.isAllowed("u1", "pedidos", "aprobar"))
                .isInstanceOf(AuthUnavailableException.class);
    }

    @Test
    void isAllowed_tokenFailure_failsClosed() {
        when(tokens.token()).thenThrow(new ServiceTokenException("kc caido", null));

        assertThatThrownBy(() -> adapter.isAllowed("u1", "pedidos", "aprobar"))
                .isInstanceOf(AuthUnavailableException.class);
    }
}
