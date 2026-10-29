package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.ItemConditionUseCase;
import com.example.msinventory.domain.exception.ConditionAlreadyExistsException;
import com.example.msinventory.domain.exception.ConditionNotFoundException;
import com.example.msinventory.domain.model.ItemCondition;
import com.example.msinventory.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemConditionController.class)
@Import(SecurityConfig.class)
class ItemConditionControllerTest {

    private static final String BASE = "/api/v1/inventory/conditions";

    @Autowired MockMvc mvc;
    @MockitoBean ItemConditionUseCase conditions;
    @MockitoBean JwtDecoder jwtDecoder;

    private static ItemCondition cond(String code, String name, boolean active) {
        return ItemCondition.builder().code(code).name(name).active(active).build();
    }

    @Test
    void findAll_200() throws Exception {
        when(conditions.findAll()).thenReturn(List.of(cond("NEW", "Nuevo", true)));
        mvc.perform(get(BASE).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("NEW"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void create_201_conflict409_blank422() throws Exception {
        when(conditions.create("X", "Equis")).thenReturn(cond("X", "Equis", true));
        when(conditions.create("NEW", "Nuevo")).thenThrow(new ConditionAlreadyExistsException("ya existe"));
        mvc.perform(post(BASE).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"Equis\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("X"));
        mvc.perform(post(BASE).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NEW\",\"name\":\"Nuevo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONDITION_ALREADY_EXISTS"));
        mvc.perform(post(BASE).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"\",\"name\":\"a\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void update_200_notFound404_missingActive422() throws Exception {
        when(conditions.update("NEW", "Nuevo2", false)).thenReturn(cond("NEW", "Nuevo2", false));
        when(conditions.update("NOPE", "n", true)).thenThrow(new ConditionNotFoundException("no"));
        mvc.perform(patch(BASE + "/NEW").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo2\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch(BASE + "/NOPE").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\",\"active\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch(BASE + "/NEW").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"n\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void withoutJwt_401() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(conditions);
    }
}
