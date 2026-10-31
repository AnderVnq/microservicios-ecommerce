package com.example.msproducts.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.msproducts.application.port.in.IgnoredDifference;
import com.example.msproducts.application.port.in.ImportResult;
import com.example.msproducts.application.port.in.ImportRow;
import com.example.msproducts.application.port.in.ProductImportUseCase;
import com.example.msproducts.application.port.in.ProductUseCase;
import com.example.msproducts.application.port.in.RowOutcome;
import com.example.msproducts.application.port.in.RowResult;
import com.example.msproducts.domain.exception.MarketsUnavailableException;
import com.example.msproducts.infrastructure.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductImportControllerTest {

    private static final String ROW = """
            {"sku":"TS-1","mainSku":"TS-1","name":"Camiseta","categoryPath":"Ropa > Camisetas",
             "weight":{"value":2,"unit":"STONE"},"dimensions":{"length":1,"width":2,"height":3,"unit":"in"},
             "referencePrice":19.99,"priceCurrency":"USD","color":"%s",
             "marketSkus":[{"marketId":7,"marketSku":"AMZ-1"}]}""";

    @Autowired MockMvc mvc;
    @MockitoBean ProductUseCase products;
    @MockitoBean ProductImportUseCase importer;
    @MockitoBean JwtDecoder jwtDecoder;

    @SuppressWarnings("unchecked")
    @Test
    void import_200_rowsMappedAndResultShape() throws Exception {
        when(importer.importRows(anyList())).thenReturn(new ImportResult(2, 1, 0, 0, 1, List.of(
                new RowResult(1, "TS-1", RowOutcome.CREATED, List.of(),
                        List.of(new IgnoredDifference("color", "Rojo", "Verde"))),
                new RowResult(2, "TS-2", RowOutcome.REJECTED, List.of("SKU repetido en la carga"), List.of()))));

        // Un color de 61 caracteres no tumba la peticion: lo rechaza la fila
        mvc.perform(post("/api/v1/products/import").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[" + ROW.formatted("x".repeat(61)) + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(0))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.rows[0].row").value(1))
                .andExpect(jsonPath("$.rows[0].sku").value("TS-1"))
                .andExpect(jsonPath("$.rows[0].result").value("CREATED"))
                .andExpect(jsonPath("$.rows[0].ignoredDifferences[0].field").value("color"))
                .andExpect(jsonPath("$.rows[0].ignoredDifferences[0].current").value("Rojo"))
                .andExpect(jsonPath("$.rows[0].ignoredDifferences[0].received").value("Verde"))
                .andExpect(jsonPath("$.rows[1].reasons[0]").value("SKU repetido en la carga"));

        ArgumentCaptor<List<ImportRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(importer).importRows(captor.capture());
        ImportRow row = captor.getValue().get(0);
        assertThat(row.sku()).isEqualTo("TS-1");
        assertThat(row.categoryPath()).isEqualTo("Ropa > Camisetas");
        assertThat(row.weight().unit()).isEqualTo("STONE");
        assertThat(row.dimensions().unit()).isEqualTo("in");
        assertThat(row.referencePrice()).isEqualByComparingTo("19.99");
        assertThat(row.marketSkus().get(0).marketSku()).isEqualTo("AMZ-1");
    }

    @Test
    void import_over5000_422() throws Exception {
        String rows = String.join(",", java.util.Collections.nCopies(5001, "{\"sku\":\"A\",\"mainSku\":\"A\"}"));

        mvc.perform(post("/api/v1/products/import").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[" + rows + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(importer, never()).importRows(any());
    }

    @Test
    void import_missingRows_422() throws Exception {
        mvc.perform(post("/api/v1/products/import").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void import_marketsDown_503() throws Exception {
        when(importer.importRows(anyList())).thenThrow(new MarketsUnavailableException("caido", null));

        mvc.perform(post("/api/v1/products/import").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[" + ROW.formatted("Rojo") + "]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MARKETS_UNAVAILABLE"));
    }
}
