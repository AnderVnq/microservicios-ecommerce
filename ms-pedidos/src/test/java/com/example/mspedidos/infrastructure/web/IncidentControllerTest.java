package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.IncidentUseCase;
import com.example.mspedidos.domain.model.IncidentScope;
import com.example.mspedidos.domain.model.IncidentStatus;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
@Import(SecurityConfig.class)
class IncidentControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean IncidentUseCase incidents;
    @MockitoBean JwtDecoder jwtDecoder;

    private static ItemIncident incident(IncidentStatus s) {
        return ItemIncident.builder().id(5L).orderItemId(1L).type(IncidentType.DELAY)
                .scope(IncidentScope.INTERNAL).description("late").status(s).openedBy("u1").build();
    }

    @Test
    void incidents_searchByStatus() throws Exception {
        when(incidents.search(IncidentStatus.OPEN, null, null)).thenReturn(List.of(incident(IncidentStatus.OPEN)));
        mvc.perform(get("/api/v1/incidents").param("status", "OPEN").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].type").value("DELAY"));
    }

    @Test
    void incidents_badFilterValue_422() throws Exception {
        mvc.perform(get("/api/v1/incidents").param("status", "FOO").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(incidents);
    }

    @Test
    void open_201() throws Exception {
        when(incidents.open(eq(7L), eq(1L), eq(IncidentType.DELAY), eq(IncidentScope.INTERNAL), any()))
                .thenReturn(incident(IncidentStatus.OPEN));
        mvc.perform(post("/api/v1/orders/7/items/1/incidents").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DELAY\",\"scope\":\"INTERNAL\",\"description\":\"late\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void open_nullType_422() throws Exception {
        mvc.perform(post("/api/v1/orders/7/items/1/incidents").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"INTERNAL\"}"))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(incidents);
    }

    @Test
    void resolve_200() throws Exception {
        when(incidents.resolve(5L)).thenReturn(incident(IncidentStatus.RESOLVED));
        mvc.perform(post("/api/v1/incidents/5/resolve").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        verify(incidents).resolve(5L);
    }
}
