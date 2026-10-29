package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.AllocationUseCase;
import com.example.msinventory.application.port.in.InventoryUnitUseCase;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort.UnitFilter;
import com.example.msinventory.domain.exception.AllocationConflictException;
import com.example.msinventory.domain.exception.UnitNotFoundException;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;
import com.example.msinventory.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryUnitController.class)
@Import(SecurityConfig.class)
class InventoryUnitControllerTest {

    private static final String BASE = "/api/v1/inventory";
    /** JWT del client credentials de ms-pedidos, unico autorizado a asignar/liberar unidades. */
    private static final RequestPostProcessor SERVICE = jwt().jwt(j -> j.claim("azp", "ms-pedidos"));

    @Autowired MockMvc mvc;
    @MockitoBean InventoryUnitUseCase units;
    @MockitoBean AllocationUseCase allocations;
    @MockitoBean JwtDecoder jwtDecoder;

    private static InventoryUnit unit(InventoryUnitStatus s) {
        return InventoryUnit.builder().id(5L).sku("SKU-1").conditionCode("NEW").warehouse(Warehouse.LIMA)
                .status(s).unitCost(new BigDecimal("10.50")).costCurrency("USD").allocatedOrderItemId(77L).build();
    }

    @Test
    void allocate_200_returnsAllocationShape() throws Exception {
        when(allocations.allocate(5L, 77L, "SKU-1")).thenReturn(unit(InventoryUnitStatus.ALLOCATED));
        mvc.perform(post(BASE + "/units/5/allocation").with(SERVICE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderItemId\":77,\"sku\":\"SKU-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitId").value(5))
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.warehouse").value("LIMA"))
                .andExpect(jsonPath("$.conditionCode").value("NEW"))
                .andExpect(jsonPath("$.unitCost").value(10.50))
                .andExpect(jsonPath("$.costCurrency").value("USD"))
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.allocatedOrderItemId").value(77));
    }

    @Test
    void allocate_conflict_409_ALLOCATION_CONFLICT() throws Exception {
        when(allocations.allocate(5L, 77L, "SKU-1")).thenThrow(new AllocationConflictException("ocupada"));
        mvc.perform(post(BASE + "/units/5/allocation").with(SERVICE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderItemId\":77,\"sku\":\"SKU-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_CONFLICT"));
    }

    @Test
    void allocation_onlyServiceClient_otherAzp403() throws Exception {
        mvc.perform(post(BASE + "/units/5/allocation").with(jwt().jwt(j -> j.claim("azp", "api-gateway")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderItemId\":77,\"sku\":\"SKU-1\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/units/5/allocation").param("orderItemId", "77")
                        .with(jwt().jwt(j -> j.claim("azp", "api-gateway"))))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/units/5/allocation").param("orderItemId", "77").with(jwt()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(allocations);
    }

    @Test
    void allocation_otherEndpoints_anyRealmJwt() throws Exception {
        when(units.findOne(5L)).thenReturn(unit(InventoryUnitStatus.ALLOCATED));
        mvc.perform(get(BASE + "/units/5").with(jwt().jwt(j -> j.claim("azp", "api-gateway"))))
                .andExpect(status().isOk());
    }

    @Test
    void release_204() throws Exception {
        mvc.perform(delete(BASE + "/units/5/allocation").param("orderItemId", "77").with(SERVICE))
                .andExpect(status().isNoContent());
        verify(allocations).release(5L, 77L);
    }

    @Test
    void release_missingOrderItemId_422() throws Exception {
        mvc.perform(delete(BASE + "/units/5/allocation").with(SERVICE))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(allocations);
    }

    @Test
    void writeOff_blankReason_422() throws Exception {
        mvc.perform(post(BASE + "/units/5/write-off").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(BASE + "/units/5/adjustment-out").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(units);
    }

    @Test
    void writeOff_200() throws Exception {
        when(units.writeOff(5L, "roto")).thenReturn(unit(InventoryUnitStatus.WRITTEN_OFF));
        mvc.perform(post(BASE + "/units/5/write-off").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"roto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WRITTEN_OFF"));
    }

    @Test
    void supplierReturn_reasonOptional_noBody() throws Exception {
        when(units.supplierReturn(5L, null)).thenReturn(unit(InventoryUnitStatus.RETURNED_TO_SUPPLIER));
        mvc.perform(post(BASE + "/units/5/supplier-return").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED_TO_SUPPLIER"));
    }

    @Test
    void transfer_requiresFields_422_andOk() throws Exception {
        mvc.perform(post(BASE + "/units/5/transfer").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toWarehouse\":\"USA\"}"))
                .andExpect(status().isUnprocessableContent());
        when(units.transfer(5L, Warehouse.USA, LocalDate.of(2026, 11, 1))).thenReturn(unit(InventoryUnitStatus.IN_TRANSIT));
        mvc.perform(post(BASE + "/units/5/transfer").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toWarehouse\":\"USA\",\"expectedArrivalDate\":\"2026-11-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    void arrive_andArriveAll() throws Exception {
        when(units.arrive(5L)).thenReturn(unit(InventoryUnitStatus.AVAILABLE));
        when(units.arriveAll(List.of(5L, 6L))).thenReturn(List.of(unit(InventoryUnitStatus.AVAILABLE)));
        mvc.perform(post(BASE + "/units/5/arrival").with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mvc.perform(post(BASE + "/arrivals").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitIds\":[5,6]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));
        mvc.perform(post(BASE + "/arrivals").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitIds\":[]}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void arriveAll_nullElement_422() throws Exception {
        mvc.perform(post(BASE + "/arrivals").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitIds\":[null]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(units);
    }

    @Test
    void updateDates_patch() throws Exception {
        // Semantica PATCH: el campo omitido llega como null y el servicio lo interpreta como "sin cambio".
        when(units.updateDates(5L, LocalDate.of(2026, 11, 1), null)).thenReturn(unit(InventoryUnitStatus.IN_TRANSIT));
        mvc.perform(patch(BASE + "/units/5").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedArrivalDate\":\"2026-11-01\"}"))
                .andExpect(status().isOk());
        verify(units).updateDates(5L, LocalDate.of(2026, 11, 1), null);
        when(units.updateDates(5L, null, null)).thenReturn(unit(InventoryUnitStatus.IN_TRANSIT));
        mvc.perform(patch(BASE + "/units/5").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(units).updateDates(5L, null, null);
    }

    @Test
    void findOne_exposesOperatorFields_andNotFound404() throws Exception {
        when(units.findOne(5L)).thenReturn(unit(InventoryUnitStatus.ALLOCATED));
        when(units.findOne(6L)).thenThrow(new UnitNotFoundException("no existe"));
        mvc.perform(get(BASE + "/units/5").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.allocatedOrderItemId").value(77))
                .andExpect(jsonPath("$.unitCost").value(10.50))
                .andExpect(jsonPath("$.warehouse").value("LIMA"));
        mvc.perform(get(BASE + "/units/6").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void search_passesFilter_andBadEnum422() throws Exception {
        when(units.search(new UnitFilter("SKU-1", InventoryUnitStatus.AVAILABLE, Warehouse.USA, "NEW",
                LocalDate.of(2026, 12, 1)))).thenReturn(List.of(unit(InventoryUnitStatus.AVAILABLE)));
        mvc.perform(get(BASE + "/units").param("sku", "SKU-1").param("status", "AVAILABLE")
                        .param("warehouse", "USA").param("conditionCode", "NEW")
                        .param("returnDeadlineBefore", "2026-12-01").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));
        mvc.perform(get(BASE + "/units").param("status", "FOO").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void illegalArgument_422() throws Exception {
        when(units.arrive(any())).thenThrow(new IllegalArgumentException("mal"));
        mvc.perform(post(BASE + "/units/5/arrival").with(jwt()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").value("mal"));
    }

    @Test
    void withoutJwt_401() throws Exception {
        mvc.perform(get(BASE + "/units/5")).andExpect(status().isUnauthorized());
    }
}
