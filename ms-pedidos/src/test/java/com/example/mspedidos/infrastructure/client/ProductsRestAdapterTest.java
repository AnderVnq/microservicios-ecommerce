package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.domain.exception.ProductsUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductsRestAdapterTest {

    private static final String BASE = "http://products";

    private MockRestServiceServer server;
    private ProductsRestAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ProductsRestAdapter(builder.build());
    }

    @Test
    void resolve_mapsResolvedOnly() {
        server.expect(requestTo(BASE + "/api/v1/variants/resolve")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.marketSkus[0].marketId").value(1))
                .andExpect(jsonPath("$.marketSkus[0].marketSku").value("FAL-1"))
                .andRespond(withSuccess("[{\"marketId\":1,\"marketSku\":\"FAL-1\",\"sku\":\"ZAP\"},"
                        + "{\"marketId\":1,\"marketSku\":\"FAL-2\",\"sku\":null}]", MediaType.APPLICATION_JSON));

        Map<String, String> result = adapter.resolve(1L, List.of("FAL-1", "FAL-2"));

        assertThat(result).containsExactly(Map.entry("FAL-1", "ZAP"));
    }

    @Test
    void resolve_chunksOf500() {
        List<String> skus = IntStream.range(0, 1001).mapToObj(i -> "S" + i).toList();
        server.expect(ExpectedCount.times(3), requestTo(BASE + "/api/v1/variants/resolve"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(adapter.resolve(1L, skus)).isEmpty();

        server.verify();
    }

    @Test
    void resolve_5xx_unavailable() {
        server.expect(requestTo(BASE + "/api/v1/variants/resolve")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.resolve(1L, List.of("A"))).isInstanceOf(ProductsUnavailableException.class);
    }

    @Test
    void resolve_401_unavailable() {
        server.expect(requestTo(BASE + "/api/v1/variants/resolve")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> adapter.resolve(1L, List.of("A"))).isInstanceOf(ProductsUnavailableException.class);
    }

    @Test
    void resolve_422_isBug() {
        server.expect(requestTo(BASE + "/api/v1/variants/resolve"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> adapter.resolve(1L, List.of("A"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolve_empty_noCall() {
        assertThat(adapter.resolve(1L, List.of())).isEmpty();
        server.verify();
    }

    @Test
    void canonical_parsesInputToStoredMap() {
        server.expect(requestTo(BASE + "/api/v1/variants/exists")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.skus[1]").value("b"))
                .andRespond(withSuccess("{\"existing\":[\"a\"],\"canonical\":{\"a\":\"A\"}}",
                        MediaType.APPLICATION_JSON));

        Map<String, String> canonical = adapter.canonical(new ArrayList<>(List.of("a", "b")));

        assertThat(canonical).containsExactly(Map.entry("a", "A"));
    }
}
