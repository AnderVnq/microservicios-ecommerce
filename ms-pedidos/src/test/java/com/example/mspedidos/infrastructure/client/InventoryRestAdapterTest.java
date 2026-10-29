package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.application.port.out.InventoryPort.AllocatedUnit;
import com.example.mspedidos.domain.exception.InventoryAllocationConflictException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.InventoryUnitNotFoundException;
import com.example.mspedidos.domain.model.Warehouse;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InventoryRestAdapterTest {

    private static final String BASE = "http://inventory";
    private static final String NOT_FOUND_BODY =
            "{\"code\":\"NOT_FOUND\",\"message\":\"Unidad no encontrada: 5\",\"details\":[]}";

    private MockRestServiceServer server;
    private InventoryRestAdapter adapter;
    private ServiceTokenProvider tokens;

    @BeforeEach
    void setUp() {
        tokens = mock(ServiceTokenProvider.class);
        when(tokens.token()).thenReturn("svc-token");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE)
                .requestInterceptor(new ServiceTokenInterceptor(tokens));
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new InventoryRestAdapter(builder.build());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void allocate_mapsWarehouseAndCost() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.orderItemId").value(9))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andRespond(withSuccess("""
                        {"unitId":5,"sku":"SKU-1","warehouse":"LIMA","conditionCode":"NEW","unitCost":123.45,
                         "costCurrency":"USD","status":"ALLOCATED","allocatedOrderItemId":9}""",
                        MediaType.APPLICATION_JSON));

        AllocatedUnit unit = adapter.allocate(5L, 9L, "SKU-1");

        assertThat(unit).isEqualTo(new AllocatedUnit(5L, Warehouse.LIMA, "NEW", new BigDecimal("123.45")));
    }

    @Test
    void allocate_409_conflict() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"UNIT_NOT_AVAILABLE\",\"message\":\"x\",\"details\":[]}"));

        assertThatThrownBy(() -> adapter.allocate(5L, 9L, "SKU-1"))
                .isInstanceOf(InventoryAllocationConflictException.class)
                .hasMessageContaining("UNIT_NOT_AVAILABLE");
    }

    @Test
    void allocate_409_withoutParsableBody_stillConflict() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation"))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("no-json"));

        assertThatThrownBy(() -> adapter.allocate(5L, 9L, "SKU-1"))
                .isInstanceOf(InventoryAllocationConflictException.class);
    }

    @Test
    void allocate_404_unitNotFound() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(NOT_FOUND_BODY));

        assertThatThrownBy(() -> adapter.allocate(5L, 9L, "SKU-1"))
                .isInstanceOf(InventoryUnitNotFoundException.class).hasMessageContaining("5");
    }

    @Test
    void allocate_500_unavailable() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.allocate(5L, 9L, "SKU-1"))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void allocate_422_unavailable() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> adapter.allocate(5L, 9L, "SKU-1"))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void allocate_tokenFailure_unavailable() {
        when(tokens.token()).thenThrow(new ServiceTokenException("kc caido", null));

        assertThatThrownBy(() -> adapter.allocate(5L, 9L, "SKU-1"))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void release_204_ok() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation?orderItemId=9"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());

        adapter.release(5L, 9L);

        server.verify();
    }

    @Test
    void release_404_unitNotFound() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation?orderItemId=9"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(NOT_FOUND_BODY));

        assertThatThrownBy(() -> adapter.release(5L, 9L)).isInstanceOf(InventoryUnitNotFoundException.class);
    }

    /** Un 404 que no es el ApiError NOT_FOUND de ms-inventory (URL mal configurada, gateway) no es "unidad inexistente". */
    @Test
    void release_404_withoutInventoryNotFoundCode_unavailable() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation?orderItemId=9"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation?orderItemId=9"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"timestamp\":\"2026-10-29T10:00:00Z\",\"status\":404,\"error\":\"Not Found\"}"));
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation?orderItemId=9"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"OTHER\",\"message\":\"x\"}"));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> adapter.release(5L, 9L)).isInstanceOf(InventoryUnavailableException.class);
        }
        server.verify();
    }

    @Test
    void allocate_404_withoutInventoryNotFoundCode_unavailable() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("<html>Not Found</html>"));

        assertThatThrownBy(() -> adapter.allocate(5L, 9L, "SKU-1"))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void release_409_conflict() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation?orderItemId=9"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"UnitNotAllocatedToItem\",\"message\":\"x\"}"));

        assertThatThrownBy(() -> adapter.release(5L, 9L))
                .isInstanceOf(InventoryAllocationConflictException.class)
                .hasMessageContaining("UnitNotAllocatedToItem");
    }

    @Test
    void release_500_unavailable() {
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation?orderItemId=9"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.release(5L, 9L)).isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void sendsServiceTokenNotUserToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        server.expect(requestTo(BASE + "/api/v1/inventory/units/5/allocation?orderItemId=9"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer svc-token"))
                .andRespond(withNoContent());

        adapter.release(5L, 9L);

        server.verify();
    }
}
