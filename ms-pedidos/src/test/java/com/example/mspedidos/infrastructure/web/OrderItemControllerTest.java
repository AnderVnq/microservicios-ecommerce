package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.application.port.in.ImporterQuotaUseCase;
import com.example.mspedidos.application.port.in.InventoryAllocationUseCase;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.StatusChangeRequestUseCase;
import com.example.mspedidos.domain.exception.AuthUnavailableException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
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

@WebMvcTest({OrderItemController.class, OrderItemSearchController.class, OrderController.class})
@Import(SecurityConfig.class)
class OrderItemControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderItemUseCase items;
    @MockitoBean InventoryAllocationUseCase allocation;
    @MockitoBean StatusChangeRequestUseCase undo;
    @MockitoBean OrderUseCase orders;
    @MockitoBean ImporterQuotaUseCase quota;
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

    @Test
    void allocate_200_mapsInventoryFields() throws Exception {
        OrderItem covered = OrderItem.builder().id(1L).orderId(7L).sku("SKU-1").unitPrice(new BigDecimal("10.50"))
                .status(OrderItemStatus.PROCESSING).importerType(ImporterType.UNASSIGNED)
                .inventoryUnitId(99L).inventoryReleasePending(false).build();
        when(allocation.allocate(7L, 1L, 99L)).thenReturn(covered);

        mvc.perform(post("/api/v1/orders/7/items/1/inventory-allocation").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inventoryUnitId\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.inventoryUnitId").value(99))
                .andExpect(jsonPath("$.inventoryReleasePending").value(false));
    }

    @Test
    void allocate_missingUnitId_422() throws Exception {
        mvc.perform(post("/api/v1/orders/7/items/1/inventory-allocation").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(allocation);
    }

    @Test
    void allocate_inventoryDown_503_INVENTORY_UNAVAILABLE() throws Exception {
        when(allocation.allocate(7L, 1L, 99L)).thenThrow(new InventoryUnavailableException("caido"));

        mvc.perform(post("/api/v1/orders/7/items/1/inventory-allocation").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inventoryUnitId\":99}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INVENTORY_UNAVAILABLE"));
    }

    @Test
    void allocate_authDown_503_AUTH_UNAVAILABLE() throws Exception {
        when(allocation.allocate(7L, 1L, 99L)).thenThrow(new AuthUnavailableException("caido"));

        mvc.perform(post("/api/v1/orders/7/items/1/inventory-allocation").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inventoryUnitId\":99}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
    }

    @Test
    void undoRequest_201_mapsResponse() throws Exception {
        when(undo.requestAllocationUndo(7L, 1L, "se cubrio mal")).thenReturn(StatusChangeRequest.builder().id(3L)
                .type(StatusChangeRequestType.ALLOCATION_UNDO).orderItemId(1L).inventoryUnitId(99L)
                .reason("se cubrio mal").status(StatusChangeRequestStatus.PENDING)
                .requestedBy("u1").requestedAt(Instant.parse("2026-03-01T10:00:00Z")).build());

        mvc.perform(post("/api/v1/orders/7/items/1/allocation-undo-requests").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"se cubrio mal\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.type").value("ALLOCATION_UNDO"))
                .andExpect(jsonPath("$.orderItemId").value(1))
                .andExpect(jsonPath("$.inventoryUnitId").value(99))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedBy").value("u1"));
    }

    @Test
    void undoRequest_appliedDirectly_200() throws Exception {
        when(undo.requestAllocationUndo(7L, 1L, "se cubrio mal")).thenReturn(StatusChangeRequest.builder().id(3L)
                .type(StatusChangeRequestType.ALLOCATION_UNDO).orderItemId(1L).inventoryUnitId(99L)
                .reason("se cubrio mal").status(StatusChangeRequestStatus.APPLIED_DIRECTLY).requestedBy("gerente")
                .decidedBy("gerente").requestedAt(Instant.parse("2026-03-01T10:00:00Z"))
                .decidedAt(Instant.parse("2026-03-01T10:00:00Z")).build());

        mvc.perform(post("/api/v1/orders/7/items/1/allocation-undo-requests").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"se cubrio mal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED_DIRECTLY"))
                .andExpect(jsonPath("$.decidedBy").value("gerente"));
    }

    @Test
    void undoRequest_authDown_503() throws Exception {
        when(undo.requestAllocationUndo(7L, 1L, "se cubrio mal")).thenThrow(new AuthUnavailableException("caido"));

        mvc.perform(post("/api/v1/orders/7/items/1/allocation-undo-requests").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"se cubrio mal\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
    }

    @Test
    void undoRequest_blankReason_422() throws Exception {
        mvc.perform(post("/api/v1/orders/7/items/1/allocation-undo-requests").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(undo);
    }

    @Test
    void itemsReleasePending_returnsFlagged() throws Exception {
        OrderItem flagged = OrderItem.builder().id(4L).orderId(7L).sku("SKU-1").unitPrice(BigDecimal.ONE)
                .status(OrderItemStatus.PROCESSING).importerType(ImporterType.UNASSIGNED)
                .inventoryReleasePending(true).build();
        when(items.findByReleasePending(true)).thenReturn(List.of(flagged));

        mvc.perform(get("/api/v1/orders/items").param("inventoryReleasePending", "true").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].inventoryReleasePending").value(true));
        verifyNoInteractions(orders);
    }

    @Test
    void literalItemsPath_winsOverOrderId_andOrderByIdStillWorks() throws Exception {
        when(items.findByReleasePending(null)).thenReturn(List.of());
        when(orders.findById(5L)).thenReturn(new OrderView(
                Order.builder().id(5L).externalOrderId("EXT-5").holderId(1L).marketId(2L)
                        .orderDate(java.time.LocalDateTime.of(2026, 3, 1, 10, 0)).itemsTotal(BigDecimal.ZERO).build(),
                List.of()));

        mvc.perform(get("/api/v1/orders/items").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verifyNoInteractions(orders);

        mvc.perform(get("/api/v1/orders/5").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
        verify(orders).findById(5L);
    }
}
