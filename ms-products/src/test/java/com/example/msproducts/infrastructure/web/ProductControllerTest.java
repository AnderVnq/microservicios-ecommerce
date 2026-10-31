package com.example.msproducts.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.msproducts.application.port.in.ProductUseCase;
import com.example.msproducts.application.port.in.ProductView;
import com.example.msproducts.application.port.in.VariantView;
import com.example.msproducts.domain.exception.MainVariantRequiredException;
import com.example.msproducts.domain.exception.MarketSkuAlreadyUsedException;
import com.example.msproducts.domain.exception.MarketsUnavailableException;
import com.example.msproducts.domain.exception.SkuAlreadyExistsException;
import com.example.msproducts.domain.exception.VariantNotFoundException;
import com.example.msproducts.domain.model.DimensionUnit;
import com.example.msproducts.domain.model.Dimensions;
import com.example.msproducts.domain.model.MarketSku;
import com.example.msproducts.domain.model.Product;
import com.example.msproducts.domain.model.ProductStatus;
import com.example.msproducts.domain.model.ProductVariant;
import com.example.msproducts.domain.model.VariantStatus;
import com.example.msproducts.domain.model.Weight;
import com.example.msproducts.domain.model.WeightUnit;
import com.example.msproducts.infrastructure.config.SecurityConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ProductController.class, VariantController.class})
@Import(SecurityConfig.class)
class ProductControllerTest {

    private static final String BODY = """
            {"name":"Polo","variants":[{"sku":"P-1","isMain":true,"weight":{"value":2,"unit":"LB"}}]}""";

    @Autowired MockMvc mvc;
    @MockitoBean ProductUseCase products;
    @MockitoBean com.example.msproducts.application.port.in.ProductImportUseCase importer;
    @MockitoBean com.example.msproducts.application.port.in.SkuResolutionUseCase resolution;
    @MockitoBean JwtDecoder jwtDecoder;

    private static ProductVariant variant() {
        return ProductVariant.builder().id(11L).productId(5L).sku("P-1").main(true)
                .weight(new Weight(new BigDecimal("2"), WeightUnit.LB)).weightKg(new BigDecimal("0.907"))
                .dimensions(new Dimensions(new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("2"),
                        DimensionUnit.IN))
                .lengthCm(new BigDecimal("25.40")).widthCm(new BigDecimal("12.70")).heightCm(new BigDecimal("5.08"))
                .status(VariantStatus.ACTIVE).build();
    }

    private static ProductView view() {
        Product p = Product.builder().id(5L).name("Polo").status(ProductStatus.ACTIVE).build();
        return new ProductView(p, List.of("Ropa"), List.of(new VariantView(variant(),
                List.of(new MarketSku(3L, 11L, 7L, "AMZ-1", "op", null)))));
    }

    @Test
    void post_201_returnsOriginalAndConvertedValues() throws Exception {
        when(products.create(any())).thenReturn(view());

        mvc.perform(post("/api/v1/products").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.categoryPath[0]").value("Ropa"))
                .andExpect(jsonPath("$.variants[0].isMain").value(true))
                .andExpect(jsonPath("$.variants[0].weight.value").value(2))
                .andExpect(jsonPath("$.variants[0].weight.unit").value("LB"))
                .andExpect(jsonPath("$.variants[0].weight.kg").value(0.907))
                .andExpect(jsonPath("$.variants[0].dimensions.unit").value("IN"))
                .andExpect(jsonPath("$.variants[0].dimensions.lengthCm").value(25.40))
                .andExpect(jsonPath("$.variants[0].marketSkus[0].marketSku").value("AMZ-1"));
    }

    @Test
    void post_unknownUnit_422() throws Exception {
        String body = """
                {"name":"Polo","variants":[{"sku":"P-1","isMain":true,
                 "dimensions":{"length":1,"width":1,"height":1,"unit":"FT"}}]}""";
        mvc.perform(post("/api/v1/products").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void longTexts_422_insteadOf500() throws Exception {
        String longSku = "S".repeat(101);
        String body = """
                {"name":"Polo","variants":[{"sku":"%s","isMain":true}]}""".formatted(longSku);
        mvc.perform(post("/api/v1/products").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(put("/api/v1/products/5").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Polo\",\"hsCode\":\"%s\"}".formatted("1".repeat(21))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/variants/11/market-skus").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketId\":7,\"marketSku\":\"%s\"}".formatted(longSku)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void post_noVariants_422() throws Exception {
        mvc.perform(post("/api/v1/products").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Polo\",\"variants\":[]}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void post_duplicateSku_409() throws Exception {
        when(products.create(any())).thenThrow(new SkuAlreadyExistsException("P-1"));
        mvc.perform(post("/api/v1/products").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_ALREADY_EXISTS"));
    }

    @Test
    void marketsDown_503() throws Exception {
        when(products.addMarketSku(eq(11L), eq(7L), eq("X"))).thenThrow(new MarketsUnavailableException("x", null));
        mvc.perform(post("/api/v1/variants/11/market-skus").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketId\":7,\"marketSku\":\"X\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MARKETS_UNAVAILABLE"));
    }

    @Test
    void marketSku_duplicate_409_andCreated_201() throws Exception {
        when(products.addMarketSku(eq(11L), eq(7L), eq("X"))).thenThrow(new MarketSkuAlreadyUsedException(7L, "X"));
        mvc.perform(post("/api/v1/variants/11/market-skus").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketId\":7,\"marketSku\":\"X\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARKET_SKU_ALREADY_USED"));

        when(products.addMarketSku(eq(11L), eq(7L), eq("Y"))).thenReturn(new MarketSku(4L, 11L, 7L, "Y", "op", null));
        mvc.perform(post("/api/v1/variants/11/market-skus").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketId\":7,\"marketSku\":\"Y\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    void putVariant_unmarkMain_409() throws Exception {
        when(products.updateVariant(eq(11L), any())).thenThrow(new MainVariantRequiredException());
        mvc.perform(put("/api/v1/variants/11").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isMain\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAIN_VARIANT_REQUIRED"));
    }

    @Test
    void getVariantBySku_andNotFound() throws Exception {
        when(products.findVariantBySku("P-1")).thenReturn(Optional.of(variant()));
        when(products.getVariant(11L)).thenReturn(view().variants().get(0));
        mvc.perform(get("/api/v1/variants").param("sku", "P-1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("P-1"));

        when(products.getVariant(99L)).thenThrow(new VariantNotFoundException(99L));
        mvc.perform(get("/api/v1/variants/99").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/variants").param("sku", "NOPE").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void search_passesFilters() throws Exception {
        when(products.search("polo", 2L, "Acme", ProductStatus.ACTIVE)).thenReturn(List.of(view()));
        mvc.perform(get("/api/v1/products").param("search", "polo").param("categoryId", "2")
                        .param("brand", "Acme").param("status", "ACTIVE").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Polo"));
    }

    @Test
    void deleteMarketSku_204() throws Exception {
        mvc.perform(delete("/api/v1/variants/11/market-skus/3").with(jwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unauthenticated_401() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
    }
}
