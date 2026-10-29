package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.CreateOrderCommand;
import com.example.mspedidos.application.port.in.ImporterQuotaUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.in.PersonQuota;
import com.example.mspedidos.application.port.in.QuotaSummary;
import com.example.mspedidos.domain.exception.ClientesUnavailableException;
import com.example.mspedidos.domain.exception.PersonNotFoundException;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderHeaderChanges;
import com.example.mspedidos.domain.model.OrderItem;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderUseCase orders;
    @MockitoBean ImporterQuotaUseCase quota;
    @MockitoBean JwtDecoder jwtDecoder;

    static final String BODY = """
            {"holderId":1,"marketId":2,"externalOrderId":"EXT-1","orderDate":"2026-03-01T10:00:00",
             "lines":[{"sku":"SKU-1","quantity":2,"unitPrice":10.50}]}""";

    private static Order order() {
        return Order.builder().id(7L).externalOrderId("EXT-1").holderId(1L).marketId(2L)
                .orderDate(LocalDateTime.of(2026, 3, 1, 10, 0)).itemsTotal(new BigDecimal("21.00")).build();
    }

    private static OrderItem unit(long id) {
        return OrderItem.builder().id(id).orderId(7L).sku("SKU-1").unitPrice(new BigDecimal("10.50"))
                .status(OrderItemStatus.PROCESSING).importerType(ImporterType.UNASSIGNED).build();
    }

    @Test
    void createOrder_201_mapsUnits() throws Exception {
        when(orders.create(any())).thenReturn(new OrderView(order(), List.of(unit(1), unit(2))));

        mvc.perform(post("/api/v1/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.items[0].importerType").value("UNASSIGNED"));

        ArgumentCaptor<CreateOrderCommand> cap = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orders).create(cap.capture());
        assertThat(cap.getValue().lines()).hasSize(1);
        assertThat(cap.getValue().lines().get(0).quantity()).isEqualTo(2);
        assertThat(cap.getValue().holderId()).isEqualTo(1L);
    }

    @Test
    void createOrder_rejectsZeroQuantity() throws Exception {
        String body = BODY.replace("\"quantity\":2", "\"quantity\":0");
        mvc.perform(post("/api/v1/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(orders);
    }

    @Test
    void createOrder_emptyLines_422() throws Exception {
        String body = """
                {"holderId":1,"marketId":2,"externalOrderId":"EXT-1","orderDate":"2026-03-01T10:00:00","lines":[]}""";
        mvc.perform(post("/api/v1/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(orders);
    }

    @Test
    void createOrder_withoutJwt_401() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test
    void createOrder_clientesDown_503() throws Exception {
        when(orders.create(any())).thenThrow(new ClientesUnavailableException("down", new RuntimeException()));
        mvc.perform(post("/api/v1/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CLIENTES_UNAVAILABLE"));
    }

    @Test
    void createOrder_personNotFound_404() throws Exception {
        when(orders.create(any())).thenThrow(new PersonNotFoundException("1"));
        mvc.perform(post("/api/v1/orders").with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
    }

    @Test
    void quota_returnsPersons() throws Exception {
        when(quota.quotaFor(1L, 2026)).thenReturn(new QuotaSummary(2026, 3, List.of(
                new PersonQuota("HOLDER", 1L, "Ana Perez", "DNI", "123", 2, 1))));

        mvc.perform(get("/api/v1/orders/importer-quota").param("holderId", "1").param("year", "2026").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.maxUnitsPerYear").value(3))
                .andExpect(jsonPath("$.persons[0].fullName").value("Ana Perez"))
                .andExpect(jsonPath("$.persons[0].availableUnits").value(1));
        verify(orders, never()).findById(anyLong());
    }

    @Test
    void getOrder_byId_200() throws Exception {
        when(orders.findById(7L)).thenReturn(new OrderView(order(), List.of(unit(1))));
        mvc.perform(get("/api/v1/orders/7").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void updateHeader_mapsEditableFields() throws Exception {
        when(orders.updateHeader(anyLong(), any())).thenReturn(order());
        mvc.perform(put("/api/v1/orders/7").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNumber\":\"N-1\",\"shippingCost\":5.0,\"note\":\"hi\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<OrderHeaderChanges> cap = ArgumentCaptor.forClass(OrderHeaderChanges.class);
        verify(orders).updateHeader(eq(7L), cap.capture());
        assertThat(cap.getValue().orderNumber()).isEqualTo("N-1");
        assertThat(cap.getValue().note()).isEqualTo("hi");
    }

    @Test
    void updateHeader_negativeMoney_422() throws Exception {
        mvc.perform(put("/api/v1/orders/7").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingCost\":-1}"))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(orders);
    }

    @Test
    void cancel_200() throws Exception {
        when(orders.cancel(7L)).thenReturn(new OrderView(order(), List.of()));
        mvc.perform(post("/api/v1/orders/7/cancel").with(jwt())).andExpect(status().isOk());
    }
}
