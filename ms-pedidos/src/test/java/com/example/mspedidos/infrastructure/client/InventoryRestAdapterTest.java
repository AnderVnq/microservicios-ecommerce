package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.application.port.out.InventoryPort.AllocatedUnit;
import com.example.mspedidos.application.port.out.InventoryPort.CreatePurchaseEntry;
import com.example.mspedidos.application.port.out.InventoryPort.PurchaseEntryResult;
import com.example.mspedidos.domain.exception.InventorySyncRejectedException;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    private static final String ENTRIES = BASE + "/api/v1/inventory/purchase-entries";

    @Test
    void createPurchaseEntry_sendsKeyAndMapsReservations() {
        server.expect(requestTo(ENTRIES)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "purchase-500-entry"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer svc-token"))
                .andExpect(jsonPath("$.purchaseId").value(500))
                .andExpect(jsonPath("$.supplierId").value(7))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.conditionCode").value("NEW"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.unitCost").value(12.5))
                .andExpect(jsonPath("$.costCurrency").value("USD"))
                .andExpect(jsonPath("$.expectedArrivalDate").value("2026-11-15"))
                .andExpect(jsonPath("$.reservations[0]").value(11))
                .andExpect(jsonPath("$.reservations[1]").value(12))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"entryId":9,"purchaseId":500,
                         "reservations":[{"orderItemId":11,"unitId":1},{"orderItemId":12,"unitId":2}],
                         "unitIds":[1,2,3]}"""));

        PurchaseEntryResult result = adapter.createPurchaseEntry("purchase-500-entry", new CreatePurchaseEntry(
                500L, 7L, "SKU-1", "NEW", 3, new BigDecimal("12.50"), "USD", LocalDate.of(2026, 11, 15),
                List.of(11L, 12L)));

        assertThat(result.entryId()).isEqualTo(9L);
        assertThat(result.unitByOrderItem()).containsExactlyInAnyOrderEntriesOf(Map.of(11L, 1L, 12L, 2L));
        server.verify();
    }

    @Test
    void createPurchaseEntry_replay200_noReservations_emptyMap() {
        server.expect(requestTo(ENTRIES)).andRespond(withSuccess(
                "{\"entryId\":9,\"purchaseId\":500,\"reservations\":[],\"unitIds\":[1]}",
                MediaType.APPLICATION_JSON));

        PurchaseEntryResult result = adapter.createPurchaseEntry("k", new CreatePurchaseEntry(
                500L, 7L, "SKU-1", "NEW", 1, BigDecimal.ONE, "USD", LocalDate.of(2026, 11, 15), List.of()));

        assertThat(result).isEqualTo(new PurchaseEntryResult(9L, Map.of()));
    }

    @Test
    void createPurchaseEntry_409_alreadyRegistered_permanent() {
        server.expect(requestTo(ENTRIES)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"PURCHASE_ALREADY_REGISTERED\",\"message\":\"x\",\"details\":[]}"));

        assertThatThrownBy(() -> adapter.createPurchaseEntry("k", new CreatePurchaseEntry(
                500L, 7L, "SKU-1", "NEW", 1, BigDecimal.ONE, "USD", LocalDate.of(2026, 11, 15), List.of())))
                .isInstanceOfSatisfying(InventorySyncRejectedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("PURCHASE_ALREADY_REGISTERED"));
    }

    @Test
    void registerArrival_sendsKeyQuantityAndItems() {
        server.expect(requestTo(ENTRIES + "/500/arrivals")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "purchase-500-arrival-3"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.orderItemIds[0]").value(11))
                .andRespond(withSuccess("{\"unitIds\":[1,2]}", MediaType.APPLICATION_JSON));

        adapter.registerPurchaseArrival("purchase-500-arrival-3", 500L, 2, List.of(11L));

        server.verify();
    }

    @Test
    void registerArrival_409_permanent() {
        server.expect(requestTo(ENTRIES + "/500/arrivals")).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"NOT_ENOUGH_PURCHASE_UNITS\",\"message\":\"x\",\"details\":[]}"));

        assertThatThrownBy(() -> adapter.registerPurchaseArrival("k", 500L, 2, List.of()))
                .isInstanceOfSatisfying(InventorySyncRejectedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_ENOUGH_PURCHASE_UNITS"));
    }

    @Test
    void registerArrival_503_transient() {
        server.expect(requestTo(ENTRIES + "/500/arrivals"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> adapter.registerPurchaseArrival("k", 500L, 2, List.of()))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void registerArrival_404_withoutNotFoundCode_transient_withCode_permanent() {
        server.expect(requestTo(ENTRIES + "/500/arrivals")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(ENTRIES + "/500/arrivals")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body(NOT_FOUND_BODY));

        assertThatThrownBy(() -> adapter.registerPurchaseArrival("k", 500L, 2, List.of()))
                .isInstanceOf(InventoryUnavailableException.class);
        assertThatThrownBy(() -> adapter.registerPurchaseArrival("k", 500L, 2, List.of()))
                .isInstanceOfSatisfying(InventorySyncRejectedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void registerArrival_401_transient_422_permanent() {
        server.expect(requestTo(ENTRIES + "/500/arrivals")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(ENTRIES + "/500/arrivals")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"IDEMPOTENCY_KEY_REUSED\",\"message\":\"x\"}"));

        assertThatThrownBy(() -> adapter.registerPurchaseArrival("k", 500L, 2, List.of()))
                .isInstanceOf(InventoryUnavailableException.class);
        assertThatThrownBy(() -> adapter.registerPurchaseArrival("k", 500L, 2, List.of()))
                .isInstanceOfSatisfying(InventorySyncRejectedException.class,
                        e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void registerArrival_ioError_transient() {
        server.expect(requestTo(ENTRIES + "/500/arrivals")).andRespond(request -> {
            throw new java.io.IOException("connection refused");
        });

        assertThatThrownBy(() -> adapter.registerPurchaseArrival("k", 500L, 2, List.of()))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void registerNotReceived_sendsReason() {
        server.expect(requestTo(ENTRIES + "/500/not-received")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "purchase-500-not-received-4"))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.orderItemIds[0]").value(12))
                .andExpect(jsonPath("$.reason").value("perdido"))
                .andRespond(withSuccess("{\"unitIds\":[2]}", MediaType.APPLICATION_JSON));

        adapter.registerPurchaseNotReceived("purchase-500-not-received-4", 500L, 1, List.of(12L), "perdido");

        server.verify();
    }

    @Test
    void isConditionActive_inactiveFalse() {
        String body = "[{\"code\":\"NEW\",\"name\":\"Nuevo\",\"active\":false},"
                + "{\"code\":\"USED\",\"name\":\"Usado\",\"active\":true}]";
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(BASE + "/api/v1/inventory/conditions")).andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }

        assertThat(adapter.isConditionActive("NEW")).isFalse();
        assertThat(adapter.isConditionActive("USED")).isTrue();
        assertThat(adapter.isConditionActive("MISSING")).isFalse();
    }

    @Test
    void isConditionActive_500_unavailable() {
        server.expect(requestTo(BASE + "/api/v1/inventory/conditions")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.isConditionActive("NEW")).isInstanceOf(InventoryUnavailableException.class);
    }
}
