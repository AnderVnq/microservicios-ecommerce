package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.NotReceivedResult;
import com.example.mspedidos.application.port.in.PurchaseUseCase;
import com.example.mspedidos.application.port.in.PurchaseView;
import com.example.mspedidos.application.port.in.RegisterPurchaseCommand;
import com.example.mspedidos.application.port.out.InventorySyncState;
import com.example.mspedidos.domain.exception.IdempotencyKeyReusedException;
import com.example.mspedidos.domain.exception.PurchaseNotFoundException;
import com.example.mspedidos.domain.exception.PurchaseQuantityExceededException;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Purchase;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PurchaseController.class)
@Import({SecurityConfig.class, RequestHasher.class})
class PurchaseControllerTest {

    private static final String BODY = "{\"supplierId\":3,\"sku\":\"SKU-1\",\"conditionCode\":\"NEW\","
            + "\"quantity\":5,\"unitCost\":10.50,\"supplierPurchaseId\":\"SP-1\","
            + "\"expectedArrivalDate\":\"2026-12-01\"}";

    @Autowired MockMvc mvc;
    @MockitoBean PurchaseUseCase purchases;
    @MockitoBean JwtDecoder jwtDecoder;

    private static Purchase purchase() {
        return Purchase.builder().id(7L).supplierId(3L).sku("SKU-1").conditionCode("NEW").quantity(5)
                .unitCost(new BigDecimal("10.50")).costCurrency("USD").supplierPurchaseId("SP-1")
                .expectedArrivalDate(LocalDate.parse("2026-12-01")).receivedQuantity(1).notReceivedQuantity(0).build();
    }

    private static PurchaseView view(boolean replayed) {
        OrderItem item = OrderItem.builder().id(11L).orderId(21L).sku("SKU-1").status(OrderItemStatus.PURCHASED)
                .purchaseId(7L).build();
        return new PurchaseView(purchase(), List.of(item), 2, List.of(), List.of(), InventorySyncState.OK,
                List.of("aviso"), replayed);
    }

    @Test
    void register_201_thenReplay200() throws Exception {
        when(purchases.register(eq("k1"), anyString(), any(RegisterPurchaseCommand.class)))
                .thenReturn(view(false)).thenReturn(view(true));
        mvc.perform(post("/api/v1/purchases").with(jwt()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.pendingQuantity").value(4))
                .andExpect(jsonPath("$.inventorySync").value("OK"))
                .andExpect(jsonPath("$.coveredItems[0].itemId").value(11))
                .andExpect(jsonPath("$.coveredItems[0].orderId").value(21))
                .andExpect(jsonPath("$.coveredItems[0].status").value("PURCHASED"))
                .andExpect(jsonPath("$.pendingSurplus").value(2))
                .andExpect(jsonPath("$.warnings[0]").value("aviso"));
        mvc.perform(post("/api/v1/purchases").with(jwt()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void register_missingKey_422() throws Exception {
        mvc.perform(post("/api/v1/purchases").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(purchases);
    }

    @Test
    void register_hashIsFieldOrderIndependent() throws Exception {
        String reordered = "{\"expectedArrivalDate\":\"2026-12-01\",\"supplierPurchaseId\":\"SP-1\",\"unitCost\":10.50,"
                + "\"quantity\":5,\"conditionCode\":\"NEW\",\"sku\":\"SKU-1\",\"supplierId\":3}";
        when(purchases.register(anyString(), anyString(), any())).thenReturn(view(false));
        mvc.perform(post("/api/v1/purchases").with(jwt()).header("Idempotency-Key", "k1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/purchases").with(jwt()).header("Idempotency-Key", "k1")
                .contentType(MediaType.APPLICATION_JSON).content(reordered)).andExpect(status().isCreated());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(purchases, org.mockito.Mockito.times(2)).register(eq("k1"), hash.capture(), any());
        assertThat(hash.getAllValues().get(0)).isEqualTo(hash.getAllValues().get(1)).hasSize(64);
    }

    @Test
    void register_keyReusedWithOtherBody_422() throws Exception {
        when(purchases.register(anyString(), anyString(), any()))
                .thenThrow(new IdempotencyKeyReusedException("clave usada"));
        mvc.perform(post("/api/v1/purchases").with(jwt()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void register_quantity501_422() throws Exception {
        mvc.perform(post("/api/v1/purchases").with(jwt()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"quantity\":5", "\"quantity\":501")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(purchases);
    }

    @Test
    void register_lockConflict_409() throws Exception {
        when(purchases.register(anyString(), anyString(), any())).thenThrow(new CannotAcquireLockException("lock"));
        mvc.perform(post("/api/v1/purchases").with(jwt()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCK_CONFLICT"));
    }

    @Test
    void arrival_201() throws Exception {
        when(purchases.registerArrival(eq(7L), eq("a1"), anyString(), eq(2))).thenReturn(view(false));
        mvc.perform(post("/api/v1/purchases/7/arrivals").with(jwt()).header("Idempotency-Key", "a1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void arrival_replay200() throws Exception {
        when(purchases.registerArrival(eq(7L), eq("a1"), anyString(), eq(2))).thenReturn(view(true));
        mvc.perform(post("/api/v1/purchases/7/arrivals").with(jwt()).header("Idempotency-Key", "a1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    void arrival_overPending_422_PURCHASE_QUANTITY_EXCEEDED() throws Exception {
        when(purchases.registerArrival(eq(7L), anyString(), anyString(), anyInt()))
                .thenThrow(new PurchaseQuantityExceededException("excede"));
        mvc.perform(post("/api/v1/purchases/7/arrivals").with(jwt()).header("Idempotency-Key", "a1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":99}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PURCHASE_QUANTITY_EXCEEDED"));
    }

    @Test
    void notReceived_pending_202_withRequest() throws Exception {
        StatusChangeRequest req = StatusChangeRequest.builder().id(55L).status(StatusChangeRequestStatus.PENDING).build();
        when(purchases.markNotReceived(eq(7L), eq("nr-1"), anyString(), eq(2), eq("dañado"))).thenReturn(new NotReceivedResult(view(false), req));
        mvc.perform(post("/api/v1/purchases/7/not-received").with(jwt()).header("Idempotency-Key", "nr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2,\"reason\":\"dañado\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.statusChangeRequest.id").value(55))
                .andExpect(jsonPath("$.statusChangeRequest.status").value("PENDING"));
    }

    @Test
    void notReceived_appliedDirectly_200_withRequest() throws Exception {
        StatusChangeRequest req = StatusChangeRequest.builder().id(56L)
                .status(StatusChangeRequestStatus.APPLIED_DIRECTLY).build();
        when(purchases.markNotReceived(eq(7L), eq("nr-1"), anyString(), eq(2), eq("x"))).thenReturn(new NotReceivedResult(view(false), req));
        mvc.perform(post("/api/v1/purchases/7/not-received").with(jwt()).header("Idempotency-Key", "nr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2,\"reason\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusChangeRequest.status").value("APPLIED_DIRECTLY"));
    }

    @Test
    void notReceived_surplusOnly_200() throws Exception {
        when(purchases.markNotReceived(eq(7L), eq("nr-1"), anyString(), eq(1), eq("x"))).thenReturn(new NotReceivedResult(view(false), null));
        mvc.perform(post("/api/v1/purchases/7/not-received").with(jwt()).header("Idempotency-Key", "nr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"reason\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusChangeRequest").doesNotExist());
    }

    @Test
    void notReceived_blankReason_422() throws Exception {
        mvc.perform(post("/api/v1/purchases/7/not-received").with(jwt()).header("Idempotency-Key", "nr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"reason\":\" \"}"))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(purchases);
    }

    @Test
    void notReceived_missingKey_422() throws Exception {
        mvc.perform(post("/api/v1/purchases/7/not-received").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"reason\":\"x\"}"))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(purchases);
    }

    /** El hash lo calcula la web con RequestHasher: mismo cuerpo -> mismo hash, otro cuerpo -> otro hash. */
    @Test
    void notReceived_passesKeyAndRequestHash() throws Exception {
        when(purchases.markNotReceived(eq(7L), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(new NotReceivedResult(view(false), null));
        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        for (String body : List.of("{\"quantity\":1,\"reason\":\"x\"}", "{\"reason\":\"x\",\"quantity\":1}",
                "{\"quantity\":2,\"reason\":\"x\"}")) {
            mvc.perform(post("/api/v1/purchases/7/not-received").with(jwt()).header("Idempotency-Key", "nr-1")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        verify(purchases, org.mockito.Mockito.times(3))
                .markNotReceived(eq(7L), eq("nr-1"), hashes.capture(), anyInt(), anyString());
        assertThat(hashes.getAllValues().get(0)).isEqualTo(new RequestHasher()
                .hash(new com.example.mspedidos.infrastructure.web.dto.NotReceivedRequest(1, "x")));
        assertThat(hashes.getAllValues().get(1)).isEqualTo(hashes.getAllValues().get(0));
        assertThat(hashes.getAllValues().get(2)).isNotEqualTo(hashes.getAllValues().get(0));
    }

    @Test
    void notReceived_keyReused_422() throws Exception {
        when(purchases.markNotReceived(eq(7L), anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new IdempotencyKeyReusedException("reusada"));
        mvc.perform(post("/api/v1/purchases/7/not-received").with(jwt()).header("Idempotency-Key", "nr-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1,\"reason\":\"x\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void retrySync_200() throws Exception {
        when(purchases.retrySync(7L)).thenReturn(view(false));
        mvc.perform(post("/api/v1/purchases/7/inventory-sync/retry").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inventorySync").value("OK"));
    }

    @Test
    void retrySync_unknown_404() throws Exception {
        when(purchases.retrySync(9L)).thenThrow(new PurchaseNotFoundException(9L));
        mvc.perform(post("/api/v1/purchases/9/inventory-sync/retry").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void findOne_200() throws Exception {
        when(purchases.findOne(7L)).thenReturn(view(false));
        mvc.perform(get("/api/v1/purchases/7").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierId").value(3));
    }

    @Test
    void search_passesFilters() throws Exception {
        when(purchases.search(any())).thenReturn(List.of(purchase()));
        mvc.perform(get("/api/v1/purchases?supplierId=3&sku=SKU-1&pending=true&inventorySync=FAILED").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].pendingQuantity").value(4));
        verify(purchases).search(new com.example.mspedidos.application.port.out.PurchaseFilter(
                3L, "SKU-1", null, null, true, InventorySyncState.FAILED));
    }

    @Test
    void withoutJwt_401() throws Exception {
        mvc.perform(get("/api/v1/purchases/7")).andExpect(status().isUnauthorized());
    }
}
