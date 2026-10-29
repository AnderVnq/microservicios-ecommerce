package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.EntryResult;
import com.example.msinventory.application.port.in.InventoryEntryUseCase;
import com.example.msinventory.application.port.in.RegisterEntryCommand;
import com.example.msinventory.domain.exception.IdempotencyKeyReusedException;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryEntryController.class)
@Import({SecurityConfig.class, RequestHasher.class})
class InventoryEntryControllerTest {

    private static final String BODY = """
            {"entryType":"STOCK_PURCHASE","sku":"SKU-1","conditionCode":"NEW","warehouse":"USA","quantity":2,
             "unitCost":10.50,"status":"AVAILABLE"}""";
    private static final String BODY_REORDERED = """
            {"status":"AVAILABLE","unitCost":10.50,"quantity":2,"warehouse":"USA","conditionCode":"NEW",
             "sku":"SKU-1","entryType":"STOCK_PURCHASE"}""";

    @Autowired MockMvc mvc;
    @MockitoBean InventoryEntryUseCase entries;
    @MockitoBean JwtDecoder jwtDecoder;

    private static EntryResult result(boolean replayed) {
        InventoryEntry entry = InventoryEntry.builder().id(9L).entryType(EntryType.STOCK_PURCHASE).quantity(2).build();
        InventoryUnit u = InventoryUnit.builder().id(1L).sku("SKU-1").conditionCode("NEW").warehouse(Warehouse.USA)
                .status(InventoryUnitStatus.AVAILABLE).unitCost(new BigDecimal("10.50")).costCurrency("USD").build();
        return new EntryResult(entry, List.of(u), List.of("factura repetida"), replayed);
    }

    @Test
    void registerEntry_201_thenReplay200() throws Exception {
        when(entries.register(eq("k1"), any(), any())).thenReturn(result(false), result(true));

        mvc.perform(post("/api/v1/inventory/entries").with(jwt()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryId").value(9))
                .andExpect(jsonPath("$.entryType").value("STOCK_PURCHASE"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.units[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.warnings[0]").value("factura repetida"));
        mvc.perform(post("/api/v1/inventory/entries").with(jwt()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryId").value(9));

        ArgumentCaptor<RegisterEntryCommand> cmd = ArgumentCaptor.forClass(RegisterEntryCommand.class);
        verify(entries, times(2)).register(eq("k1"), any(), cmd.capture());
        assertThat(cmd.getValue().sku()).isEqualTo("SKU-1");
        assertThat(cmd.getValue().quantity()).isEqualTo(2);
        assertThat(cmd.getValue().unitCost()).isEqualByComparingTo("10.50");
        assertThat(cmd.getValue().warehouse()).isEqualTo(Warehouse.USA);
    }

    @Test
    void registerEntry_missingIdempotencyKey_422() throws Exception {
        mvc.perform(post("/api/v1/inventory/entries").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/inventory/entries").with(jwt()).header("Idempotency-Key", "  ")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(entries);
    }

    @Test
    void registerEntry_hashIsOrderIndependent() throws Exception {
        when(entries.register(any(), any(), any())).thenReturn(result(false));
        mvc.perform(post("/api/v1/inventory/entries").with(jwt()).header("Idempotency-Key", "a")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/inventory/entries").with(jwt()).header("Idempotency-Key", "b")
                .contentType(MediaType.APPLICATION_JSON).content(BODY_REORDERED)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/inventory/entries").with(jwt()).header("Idempotency-Key", "c")
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"quantity\":2", "\"quantity\":3")))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(entries, times(3)).register(any(), hash.capture(), any());
        assertThat(hash.getAllValues().get(0)).matches("[0-9a-f]{64}");
        assertThat(hash.getAllValues().get(0)).isEqualTo(hash.getAllValues().get(1));
        assertThat(hash.getAllValues().get(2)).isNotEqualTo(hash.getAllValues().get(0));
    }

    @Test
    void registerEntry_keyReused_422() throws Exception {
        when(entries.register(any(), any(), any())).thenThrow(new IdempotencyKeyReusedException("otra"));
        mvc.perform(post("/api/v1/inventory/entries").with(jwt()).header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void registerEntry_invalidBody_422() throws Exception {
        for (String bad : List.of(
                BODY.replace("\"quantity\":2", "\"quantity\":0"),
                BODY.replace("10.50", "-1"),
                BODY.replace("\"sku\":\"SKU-1\",", ""),
                BODY.replace("AVAILABLE", "BOGUS"),
                BODY.replace("\"quantity\":2", "\"quantity\":501"),
                BODY.replace("\"unitCost\":10.50", "\"unitCost\":10.50,\"costCurrency\":\"usd\""),
                BODY.replace("\"unitCost\":10.50", "\"unitCost\":10.50,\"costCurrency\":\"USDX\""))) {
            mvc.perform(post("/api/v1/inventory/entries").with(jwt()).header("Idempotency-Key", "k")
                            .contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(entries);
    }

    @Test
    void withoutJwt_401() throws Exception {
        mvc.perform(post("/api/v1/inventory/entries").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
