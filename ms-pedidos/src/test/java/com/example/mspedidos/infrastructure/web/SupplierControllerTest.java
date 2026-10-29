package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.SupplierUseCase;
import com.example.mspedidos.domain.exception.SupplierNameTakenException;
import com.example.mspedidos.domain.exception.SupplierNotFoundException;
import com.example.mspedidos.domain.model.Supplier;
import com.example.mspedidos.domain.model.SupplierStatus;
import com.example.mspedidos.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
@Import(SecurityConfig.class)
class SupplierControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SupplierUseCase suppliers;
    @MockitoBean JwtDecoder jwtDecoder;

    private static Supplier supplier() {
        return Supplier.builder().id(3L).name("Acme").status(SupplierStatus.ACTIVE).country("US").build();
    }

    @Test
    void create_201() throws Exception {
        when(suppliers.create("Acme", "US", null, null)).thenReturn(supplier());
        mvc.perform(post("/api/v1/suppliers").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"country\":\"US\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_invalidCountry_422() throws Exception {
        mvc.perform(post("/api/v1/suppliers").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"country\":\"usa\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(suppliers);
    }

    @Test
    void create_duplicate_409() throws Exception {
        when(suppliers.create("Acme", "US", null, null)).thenThrow(new SupplierNameTakenException("dup"));
        mvc.perform(post("/api/v1/suppliers").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"country\":\"US\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPLIER_NAME_TAKEN"));
    }

    @Test
    void get_unknown_404() throws Exception {
        when(suppliers.findOne(9L)).thenThrow(new SupplierNotFoundException(9L));
        mvc.perform(get("/api/v1/suppliers/9").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void search_passesFilters() throws Exception {
        when(suppliers.search(SupplierStatus.ACTIVE, "US", "ac")).thenReturn(List.of(supplier()));
        mvc.perform(get("/api/v1/suppliers").param("status", "ACTIVE").param("country", "US")
                        .param("name", "ac").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Acme"));
    }

    @Test
    void patch_nullFieldsPassedThrough() throws Exception {
        when(suppliers.update(3L, null, null, null, null, SupplierStatus.INACTIVE)).thenReturn(supplier());
        mvc.perform(patch("/api/v1/suppliers/3").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        verify(suppliers).update(3L, null, null, null, null, SupplierStatus.INACTIVE);
    }
}
