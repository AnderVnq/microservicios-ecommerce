package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.application.port.out.InventoryPort;
import com.example.mspedidos.domain.exception.InventoryAllocationConflictException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.InventoryUnitNotFoundException;
import com.example.mspedidos.domain.model.Warehouse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.function.Supplier;

@Component
class InventoryRestAdapter implements InventoryPort {

    private static final String NOT_FOUND_CODE = "NOT_FOUND";

    private final RestClient restClient;

    InventoryRestAdapter(@Qualifier("inventoryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AllocatedUnit allocate(Long unitId, Long orderItemId, String sku) {
        UnitResponse body = call(unitId, () -> restClient.post()
                .uri("/api/v1/inventory/units/{id}/allocation", unitId)
                .body(new AllocationRequest(orderItemId, sku))
                .retrieve()
                .body(UnitResponse.class));
        if (body == null || body.warehouse() == null) {
            throw new InventoryUnavailableException("ms-inventory respondio sin datos de la unidad " + unitId);
        }
        try {
            return new AllocatedUnit(body.unitId(), Warehouse.valueOf(body.warehouse()),
                    body.conditionCode(), body.unitCost());
        } catch (IllegalArgumentException e) {
            throw new InventoryUnavailableException("ms-inventory respondio un almacen desconocido: " + body.warehouse());
        }
    }

    @Override
    public void release(Long unitId, Long orderItemId) {
        call(unitId, () -> {
            restClient.delete()
                    .uri("/api/v1/inventory/units/{id}/allocation?orderItemId={orderItemId}", unitId, orderItemId)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    private <T> T call(Long unitId, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                // solo el ApiError NOT_FOUND de ms-inventory es "unidad inexistente"; otro 404 (URL mal configurada,
                // gateway) no debe tomarse como liberada
                if (NOT_FOUND_CODE.equals(remoteCode(e, null))) {
                    throw new InventoryUnitNotFoundException(unitId);
                }
                throw new InventoryUnavailableException("ms-inventory respondio 404 sin codigo NOT_FOUND");
            }
            if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw new InventoryAllocationConflictException(
                        "Conflicto de asignacion en ms-inventory (unidad " + unitId + "): " + remoteCode(e, "CONFLICT"));
            }
            throw new InventoryUnavailableException("ms-inventory respondio " + e.getStatusCode());
        } catch (ServiceTokenException e) {
            throw new InventoryUnavailableException("No se pudo obtener el token de servicio para ms-inventory");
        } catch (RestClientException e) {
            throw new InventoryUnavailableException("ms-inventory no disponible: " + e.getMessage());
        }
    }

    /** Campo {@code code} del ApiError remoto, o {@code fallback} si el cuerpo no lo trae. */
    private static String remoteCode(RestClientResponseException e, String fallback) {
        try {
            RemoteError error = e.getResponseBodyAs(RemoteError.class);
            if (error != null && error.code() != null) {
                return error.code();
            }
        } catch (RuntimeException ignored) {
            // cuerpo no parseable: se usa el codigo generico
        }
        return fallback;
    }

    record AllocationRequest(Long orderItemId, String sku) {
    }

    record UnitResponse(Long unitId, String sku, String warehouse, String conditionCode, BigDecimal unitCost,
                        String costCurrency, String status, Long allocatedOrderItemId) {
    }

    record RemoteError(String code, String message) {
    }
}
