package com.example.msproducts.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.msproducts.domain.exception.MarketsUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class MarketsRestAdapterTest {

    private static final String BASE = "http://markets";

    private MockRestServiceServer server;
    private MarketsRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE)
                .requestInterceptor(new AuthorizationForwardingInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new MarketsRestAdapter(builder.build());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void market200_true() {
        server.expect(requestTo(BASE + "/api/v1/markets/3")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":3}", MediaType.APPLICATION_JSON));
        assertThat(adapter.exists(3L)).isTrue();
        server.verify();
    }

    @Test
    void market404_false() {
        server.expect(requestTo(BASE + "/api/v1/markets/3")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(adapter.exists(3L)).isFalse();
    }

    @Test
    void market5xx_unavailable() {
        server.expect(requestTo(BASE + "/api/v1/markets/3")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> adapter.exists(3L)).isInstanceOf(MarketsUnavailableException.class);
    }

    @Test
    void forwardsAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        server.expect(requestTo(BASE + "/api/v1/markets/3"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer abc"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        adapter.exists(3L);
        server.verify();
    }
}
