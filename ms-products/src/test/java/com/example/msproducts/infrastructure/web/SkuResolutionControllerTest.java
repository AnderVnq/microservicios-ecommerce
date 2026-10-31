package com.example.msproducts.infrastructure.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.msproducts.application.port.in.ProductUseCase;
import com.example.msproducts.application.port.in.SkuResolution;
import com.example.msproducts.application.port.in.SkuResolutionUseCase;
import com.example.msproducts.infrastructure.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VariantController.class)
@Import(SecurityConfig.class)
class SkuResolutionControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProductUseCase products;
    @MockitoBean SkuResolutionUseCase resolution;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void resolve_200_nullForUnknown() throws Exception {
        when(resolution.resolve(org.mockito.ArgumentMatchers.anyList())).thenReturn(
                List.of(new SkuResolution(1L, "fal-1", "ZAP-X-42"), new SkuResolution(1L, "x", null)));

        mvc.perform(post("/api/v1/variants/resolve").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketSkus\":[{\"marketId\":1,\"marketSku\":\"fal-1\"},{\"marketId\":1,\"marketSku\":\"x\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].marketSku").value("fal-1"))
                .andExpect(jsonPath("$[0].sku").value("ZAP-X-42"))
                .andExpect(jsonPath("$[1].sku").doesNotExist());
    }

    @Test
    void resolve_missingList_422() throws Exception {
        mvc.perform(post("/api/v1/variants/resolve").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void exists_200() throws Exception {
        when(resolution.canonical(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(java.util.Map.of(" abc-1 ", "ABC-1"));

        mvc.perform(post("/api/v1/variants/exists").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skus\":[\" abc-1 \",\"b\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existing.length()").value(1))
                .andExpect(jsonPath("$.existing[0]").value(" abc-1 "))
                .andExpect(jsonPath("$.canonical[' abc-1 ']").value("ABC-1"))
                .andExpect(jsonPath("$.canonical.b").doesNotExist());
    }

    @Test
    void resolve_unauthenticated_401() throws Exception {
        mvc.perform(post("/api/v1/variants/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marketSkus\":[]}"))
                .andExpect(status().isUnauthorized());
    }
}
