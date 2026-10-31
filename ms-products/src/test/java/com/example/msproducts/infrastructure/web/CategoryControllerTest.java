package com.example.msproducts.infrastructure.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.msproducts.application.port.in.CategoryUseCase;
import com.example.msproducts.domain.exception.CategoryAlreadyExistsException;
import com.example.msproducts.domain.model.Category;
import com.example.msproducts.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CategoryUseCase categories;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void post_201() throws Exception {
        when(categories.create("Ropa", null)).thenReturn(
                Category.builder().id(5L).name("Ropa").level(1).active(true).build());
        mvc.perform(post("/api/v1/categories").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ropa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.level").value(1));
    }

    @Test
    void post_duplicate_409() throws Exception {
        when(categories.create("Ropa", null)).thenThrow(new CategoryAlreadyExistsException("Ropa"));
        mvc.perform(post("/api/v1/categories").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ropa\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"));
    }

    @Test
    void post_blankName_422() throws Exception {
        mvc.perform(post("/api/v1/categories").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unauthenticated_401() throws Exception {
        mvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized());
    }
}
