package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.KardexLine;
import com.example.msinventory.application.port.in.KardexReport;
import com.example.msinventory.application.port.in.StockQueryUseCase;
import com.example.msinventory.application.port.in.StockRow;
import com.example.msinventory.domain.model.MovementType;
import com.example.msinventory.domain.model.Warehouse;
import com.example.msinventory.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@Import(SecurityConfig.class)
class StockControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean StockQueryUseCase stock;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void stock_200() throws Exception {
        when(stock.stock("SKU-1", Warehouse.USA, null))
                .thenReturn(List.of(new StockRow("SKU-1", "NEW", Warehouse.USA, 1, 2, 3, 0)));
        mvc.perform(get("/api/v1/inventory/stock").param("sku", "SKU-1").param("warehouse", "USA").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inTransit").value(1))
                .andExpect(jsonPath("$[0].available").value(2))
                .andExpect(jsonPath("$[0].total").value(3));
    }

    @Test
    void kardex_200() throws Exception {
        KardexLine line = new KardexLine(Instant.parse("2026-10-01T10:00:00Z"), MovementType.ARRIVAL, 5L, null, 1, 0,
                new BigDecimal("10.00"), 1, new BigDecimal("10.00"));
        when(stock.kardex("SKU-1", null, null, "USD", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(new KardexReport("SKU-1", null, null, "USD", 0, BigDecimal.ZERO, List.of(line), 1,
                        new BigDecimal("10.00")));
        mvc.perform(get("/api/v1/inventory/kardex").param("sku", "SKU-1").param("currency", "USD")
                        .param("from", "2026-10-01").param("to", "2026-10-31").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.lines[0].type").value("ARRIVAL"))
                .andExpect(jsonPath("$.closingQuantity").value(1));
    }

    @Test
    void kardex_requiresSku_422() throws Exception {
        mvc.perform(get("/api/v1/inventory/kardex").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/inventory/kardex").param("sku", "S").param("from", "not-a-date").with(jwt()))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(stock);
    }

    @Test
    void withoutJwt_401() throws Exception {
        mvc.perform(get("/api/v1/inventory/stock")).andExpect(status().isUnauthorized());
    }
}
