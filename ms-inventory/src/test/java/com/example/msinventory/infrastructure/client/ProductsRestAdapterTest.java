package com.example.msinventory.infrastructure.client;

import com.example.msinventory.domain.exception.ProductsUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductsRestAdapterTest {

    private static final String BASE = "http://products";

    private MockRestServiceServer server;
    private ProductsRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .requestInterceptor(new AuthorizationForwardingInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ProductsRestAdapter(builder.build());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void exists_forwardsAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        server.expect(requestTo(BASE + "/api/v1/variants/exists")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andExpect(content().json("{\"skus\":[\"SKU-1\"]}"))
                .andRespond(withSuccess("{\"existing\":[\"SKU-1\"],\"canonical\":{\"SKU-1\":\"SKU-1\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(adapter.canonicalSku("SKU-1")).contains("SKU-1");
        server.verify();
    }

    @Test
    void canonical_returnsStoredSpelling() {
        server.expect(requestTo(BASE + "/api/v1/variants/exists"))
                .andExpect(content().json("{\"skus\":[\"abc-1\"]}"))
                .andRespond(withSuccess("{\"existing\":[\"abc-1\"],\"canonical\":{\"abc-1\":\"ABC-1\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(adapter.canonicalSku("abc-1")).contains("ABC-1");
    }

    @Test
    void exists_notInResponse_empty() {
        server.expect(requestTo(BASE + "/api/v1/variants/exists"))
                .andRespond(withSuccess("{\"existing\":[],\"canonical\":{}}", MediaType.APPLICATION_JSON));

        assertThat(adapter.canonicalSku("SKU-9")).isEmpty();
    }

    @Test
    void exists_5xxAndAuthStatuses_unavailable() {
        for (HttpStatus s : new HttpStatus[]{HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN, HttpStatus.REQUEST_TIMEOUT, HttpStatus.TOO_MANY_REQUESTS}) {
            server.reset();
            server.expect(requestTo(BASE + "/api/v1/variants/exists")).andRespond(withStatus(s));
            assertThatThrownBy(() -> adapter.canonicalSku("SKU-1")).isInstanceOf(ProductsUnavailableException.class);
        }
    }

    @Test
    void exists_other4xx_illegalState() {
        server.expect(requestTo(BASE + "/api/v1/variants/exists")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.canonicalSku("SKU-1")).isInstanceOf(IllegalStateException.class);
    }
}
