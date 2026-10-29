package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.PurchaseEntryUseCase;
import com.example.msinventory.application.port.in.PurchaseEntryUseCase.CreatePurchaseEntryCommand;
import com.example.msinventory.application.port.in.PurchaseEntryUseCase.PurchaseEntryResult;
import com.example.msinventory.domain.exception.IdempotencyKeyReusedException;
import com.example.msinventory.domain.exception.NotEnoughPurchaseUnitsException;
import com.example.msinventory.domain.model.EntryType;
import com.example.msinventory.domain.model.InventoryEntry;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;
import com.example.msinventory.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PurchaseEntryController.class)
@Import({SecurityConfig.class, RequestHasher.class})
class PurchaseEntryControllerTest {

    private static final String BASE = "/api/v1/inventory/purchase-entries";
    private static final RequestPostProcessor SERVICE = jwt().jwt(j -> j.claim("azp", "ms-pedidos"));
    private static final String CREATE = """
            {"purchaseId":500,"supplierId":3,"sku":"SKU-1","conditionCode":"NEW","quantity":2,"unitCost":12.50,
             "costCurrency":"USD","expectedArrivalDate":"2026-11-20","reservations":[11]}""";

    @Autowired MockMvc mvc;
    @MockitoBean PurchaseEntryUseCase purchases;
    @MockitoBean JwtDecoder jwtDecoder;

    private static PurchaseEntryResult result(boolean replayed) {
        InventoryEntry entry = InventoryEntry.builder().id(9L).entryType(EntryType.SUPPLIER_PURCHASE).quantity(2)
                .purchaseId(500L).supplierId(3L).build();
        InventoryUnit reserved = InventoryUnit.builder().id(1L).sku("SKU-1").conditionCode("NEW")
                .warehouse(Warehouse.USA).status(InventoryUnitStatus.IN_TRANSIT).allocatedOrderItemId(11L).build();
        InventoryUnit free = InventoryUnit.builder().id(2L).sku("SKU-1").conditionCode("NEW")
                .warehouse(Warehouse.USA).status(InventoryUnitStatus.IN_TRANSIT).build();
        return new PurchaseEntryResult(entry, List.of(reserved, free), List.of(11L), replayed);
    }

    @Test
    void createPurchaseEntry_201_thenReplay200() throws Exception {
        when(purchases.create(eq("k1"), any(), any())).thenReturn(result(false), result(true));

        mvc.perform(post(BASE).with(SERVICE).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryId").value(9))
                .andExpect(jsonPath("$.purchaseId").value(500))
                .andExpect(jsonPath("$.reservations[0].orderItemId").value(11))
                .andExpect(jsonPath("$.reservations[0].unitId").value(1))
                .andExpect(jsonPath("$.unitIds[0]").value(1))
                .andExpect(jsonPath("$.unitIds[1]").value(2));
        mvc.perform(post(BASE).with(SERVICE).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isOk());

        ArgumentCaptor<CreatePurchaseEntryCommand> cmd = ArgumentCaptor.forClass(CreatePurchaseEntryCommand.class);
        verify(purchases, org.mockito.Mockito.times(2)).create(eq("k1"), any(), cmd.capture());
        assertThat(cmd.getValue().reservations()).containsExactly(11L);
        assertThat(cmd.getValue().unitCost()).isEqualByComparingTo("12.50");
    }

    @Test
    void createPurchaseEntry_otherBodySameKey_422() throws Exception {
        when(purchases.create(eq("k1"), any(), any())).thenThrow(new IdempotencyKeyReusedException("otra"));
        mvc.perform(post(BASE).with(SERVICE).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void purchaseEntries_wrongAzp_403() throws Exception {
        for (RequestPostProcessor token : List.of(jwt(), jwt().jwt(j -> j.claim("azp", "api-gateway")))) {
            mvc.perform(post(BASE).with(token).header("Idempotency-Key", "k1")
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                    .andExpect(status().isForbidden());
            mvc.perform(post(BASE + "/500/arrivals").with(token).header("Idempotency-Key", "k1")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(BASE + "/500/not-received").with(token).header("Idempotency-Key", "k1")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1,\"reason\":\"x\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(purchases);
    }

    @Test
    void missingIdempotencyKey_422() throws Exception {
        mvc.perform(post(BASE).with(SERVICE).contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(BASE + "/500/arrivals").with(SERVICE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post(BASE + "/500/not-received").with(SERVICE).header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1,\"reason\":\"x\"}"))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(purchases);
    }

    @Test
    void createPurchaseEntry_invalidBody_422() throws Exception {
        mvc.perform(post(BASE).with(SERVICE).header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purchaseId\":500,\"quantity\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void arrivals_200_unitIds() throws Exception {
        when(purchases.registerArrivals(eq("a1"), any(), eq(500L), eq(2), anyList())).thenReturn(List.of(1L, 2L));

        mvc.perform(post(BASE + "/500/arrivals").with(SERVICE).header("Idempotency-Key", "a1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2,\"orderItemIds\":[11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitIds[0]").value(1))
                .andExpect(jsonPath("$.unitIds[1]").value(2));
        verify(purchases).registerArrivals(eq("a1"), any(), eq(500L), eq(2), eq(List.of(11L)));
    }

    @Test
    void arrivals_notEnough_409() throws Exception {
        when(purchases.registerArrivals(any(), any(), any(), anyInt(), anyList()))
                .thenThrow(new NotEnoughPurchaseUnitsException("faltan"));
        mvc.perform(post(BASE + "/500/arrivals").with(SERVICE).header("Idempotency-Key", "a1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_PURCHASE_UNITS"));
    }

    @Test
    void notReceived_200_unitIds() throws Exception {
        when(purchases.markNotReceived(eq("n1"), any(), eq(500L), eq(1), anyList(), eq("perdida")))
                .thenReturn(List.of(2L));
        mvc.perform(post(BASE + "/500/not-received").with(SERVICE).header("Idempotency-Key", "n1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"orderItemIds\":[],\"reason\":\"perdida\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitIds[0]").value(2));
    }
}
