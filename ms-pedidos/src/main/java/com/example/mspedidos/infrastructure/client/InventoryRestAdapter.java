package com.example.mspedidos.infrastructure.client;

import com.example.mspedidos.application.port.out.InventoryPort;
import com.example.mspedidos.domain.exception.InventoryAllocationConflictException;
import com.example.mspedidos.domain.exception.InventorySyncRejectedException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.InventoryUnitNotFoundException;
import com.example.mspedidos.domain.model.Warehouse;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
class InventoryRestAdapter implements InventoryPort {

    private static final String NOT_FOUND_CODE = "NOT_FOUND";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String ENTRIES = "/api/v1/inventory/purchase-entries";

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

    @Override
    public boolean isConditionActive(String code) {
        ConditionResponse[] conditions;
        try {
            conditions = restClient.get().uri("/api/v1/inventory/conditions").retrieve()
                    .body(ConditionResponse[].class);
        } catch (ServiceTokenException e) {
            throw new InventoryUnavailableException("No se pudo obtener el token de servicio para ms-inventory");
        } catch (RestClientException e) {
            throw new InventoryUnavailableException("ms-inventory no disponible: " + e.getMessage());
        }
        if (conditions == null) {
            throw new InventoryUnavailableException("ms-inventory respondio sin condiciones");
        }
        return Arrays.stream(conditions).anyMatch(c -> c != null && c.active() && code.equals(c.code()));
    }

    @Override
    public PurchaseEntryResult createPurchaseEntry(String key, CreatePurchaseEntry body) {
        PurchaseEntryResponse response = syncCall(() -> restClient.post()
                .uri(ENTRIES)
                .header(IDEMPOTENCY_KEY, key)
                .body(body)
                .retrieve()
                .body(PurchaseEntryResponse.class));
        if (response == null || response.entryId() == null) {
            throw new InventoryUnavailableException("ms-inventory respondio sin la entrada de la compra "
                    + body.purchaseId());
        }
        Map<Long, Long> unitByOrderItem = new LinkedHashMap<>();
        if (response.reservations() != null) {
            response.reservations().forEach(r -> unitByOrderItem.put(r.orderItemId(), r.unitId()));
        }
        return new PurchaseEntryResult(response.entryId(), Map.copyOf(unitByOrderItem));
    }

    @Override
    public void registerPurchaseArrival(String key, Long purchaseId, int quantity, List<Long> orderItemIds) {
        postPurchaseUnits(key, "/arrivals", purchaseId, new PurchaseUnitsRequest(quantity, orderItemIds, null));
    }

    @Override
    public void registerPurchaseNotReceived(String key, Long purchaseId, int quantity, List<Long> orderItemIds,
                                            String reason) {
        postPurchaseUnits(key, "/not-received", purchaseId, new PurchaseUnitsRequest(quantity, orderItemIds, reason));
    }

    private void postPurchaseUnits(String key, String path, Long purchaseId, PurchaseUnitsRequest body) {
        syncCall(() -> restClient.post()
                .uri(ENTRIES + "/{purchaseId}" + path, purchaseId)
                .header(IDEMPOTENCY_KEY, key)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * Operaciones de compras: 5xx, 401 (token), 408, 429, IO y un 404 sin el ApiError NOT_FOUND son transitorios;
     * cualquier otro 4xx es un rechazo definitivo con el codigo remoto.
     */
    private <T> T syncCall(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (!e.getStatusCode().is4xxClientError() || status == 401 || status == 408 || status == 429) {
                throw new InventoryUnavailableException("ms-inventory respondio " + e.getStatusCode());
            }
            String code = remoteCode(e, null);
            if (status == 404 && !NOT_FOUND_CODE.equals(code)) {
                throw new InventoryUnavailableException("ms-inventory respondio 404 sin codigo NOT_FOUND");
            }
            String remote = code != null ? code : "HTTP_" + status;
            throw new InventorySyncRejectedException(remote,
                    "ms-inventory rechazo la operacion (" + status + " " + remote + "): " + remoteMessage(e));
        } catch (ServiceTokenException e) {
            throw new InventoryUnavailableException("No se pudo obtener el token de servicio para ms-inventory");
        } catch (RestClientException e) {
            throw new InventoryUnavailableException("ms-inventory no disponible: " + e.getMessage());
        }
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

    private static String remoteMessage(RestClientResponseException e) {
        try {
            RemoteError error = e.getResponseBodyAs(RemoteError.class);
            if (error != null && error.message() != null) {
                return error.message();
            }
        } catch (RuntimeException ignored) {
            // cuerpo no parseable
        }
        return "";
    }

    record AllocationRequest(Long orderItemId, String sku) {
    }

    record UnitResponse(Long unitId, String sku, String warehouse, String conditionCode, BigDecimal unitCost,
                        String costCurrency, String status, Long allocatedOrderItemId) {
    }

    record RemoteError(String code, String message) {
    }

    record ConditionResponse(String code, String name, boolean active) {
    }

    record PurchaseEntryResponse(Long entryId, Long purchaseId, List<Reservation> reservations, List<Long> unitIds) {
    }

    record Reservation(Long orderItemId, Long unitId) {
    }

    /** {@code reason} solo se envia en no recibidas (null se omite). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PurchaseUnitsRequest(int quantity, List<Long> orderItemIds, String reason) {
    }
}
