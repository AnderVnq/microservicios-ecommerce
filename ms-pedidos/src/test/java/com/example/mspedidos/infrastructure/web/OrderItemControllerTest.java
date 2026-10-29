package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.domain.exception.ImporterRequiredException;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.exception.QuotaExceededException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.infrastructure.config.SecurityConfig;
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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderItemController.class)
@Import(SecurityConfig.class)
class OrderItemControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderItemUseCase items;
    @MockitoBean JwtDecoder jwtDecoder;

    private static OrderItem unit(long id) {
        return OrderItem.builder().id(id).orderId(7L).sku("SKU-1").unitPrice(new BigDecimal("10.50"))
                .status(OrderItemStatus.PROCESSING).importerType(ImporterType.UNASSIGNED).build();
    }

    @Test
    void addUnits_201() throws Exception {
        when(items.addUnits(eq(7L), any())).thenReturn(List.of(unit(1), unit(2)));
        mvc.perform(post("/api/v1/orders/7/items").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-1\",\"quantity\":2,\"unitPrice\":10.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void addUnits_blankSku_422() throws Exception {
        mvc.perform(post("/api/v1/orders/7/items").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\" \",\"quantity\":2,\"unitPrice\":10.5}"))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(items);
    }

    @Test
    void getItem_notFound_404() throws Exception {
        when(items.findOne(7L, 99L)).thenThrow(new OrderItemNotFoundException(99L));
        mvc.perform(get("/api/v1/orders/7/items/99").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void listItems_200() throws Exception {
        when(items.findByOrder(7L)).thenReturn(List.of(unit(1)));
        mvc.perform(get("/api/v1/orders/7/items").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void updateItem_200() throws Exception {
        when(items.updateDetails(eq(7L), eq(1L), any(), any(), any())).thenReturn(unit(1));
        mvc.perform(put("/api/v1/orders/7/items/1").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-9\",\"unitPrice\":3}"))
                .andExpect(status().isOk());
    }

    @Test
    void assignImporter_quotaExceeded_409_QUOTA_EXCEEDED() throws Exception {
        when(items.assignImporter(eq(7L), eq(1L), any())).thenThrow(new QuotaExceededException("cupo"));
        mvc.perform(put("/api/v1/orders/7/items/1/importer").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importerType\":\"HOLDER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
        ArgumentCaptor<AssignImporterCommand> cap = ArgumentCaptor.forClass(AssignImporterCommand.class);
        verify(items).assignImporter(eq(7L), eq(1L), cap.capture());
        assertThat(cap.getValue().importerType()).isEqualTo(ImporterType.HOLDER);
    }

    @Test
    void assignImporter_missingType_422() throws Exception {
        mvc.perform(put("/api/v1/orders/7/items/1/importer").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(items);
    }

    @Test
    void changeStatus_standby_409_IMPORTER_REQUIRED() throws Exception {
        when(items.changeStatus(eq(7L), eq(1L), eq(OrderItemStatus.READY_TO_SHIP), any(), any()))
                .thenThrow(new ImporterRequiredException("sin importador"));
        mvc.perform(patch("/api/v1/orders/7/items/1/status").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY_TO_SHIP\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORTER_REQUIRED"));
    }

    @Test
    void changeStatus_unknownEnum_422() throws Exception {
        mvc.perform(patch("/api/v1/orders/7/items/1/status").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FOO\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(items);
    }

    @Test
    void changeStatus_missingStatus_422() throws Exception {
        mvc.perform(patch("/api/v1/orders/7/items/1/status").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void history_200() throws Exception {
        when(items.history(7L, 1L)).thenReturn(List.of(new OrderItemHistoryEntry(1L, 1L,
                HistoryEventType.values()[0], null, "PROCESSING", "u1", Instant.parse("2026-03-01T10:00:00Z"))));
        mvc.perform(get("/api/v1/orders/7/items/1/history").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performedBy").value("u1"))
                .andExpect(jsonPath("$[0].toValue").value("PROCESSING"));
    }
}
