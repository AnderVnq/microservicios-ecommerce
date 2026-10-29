package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.AllocationUndoUseCase;
import com.example.mspedidos.application.port.in.InventoryAllocationUseCase;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.infrastructure.web.dto.AllocateInventoryRequest;
import com.example.mspedidos.infrastructure.web.dto.AllocationUndoRequestResponse;
import com.example.mspedidos.infrastructure.web.dto.UndoRequestCreateRequest;
import com.example.mspedidos.infrastructure.web.dto.AssignImporterRequest;
import com.example.mspedidos.infrastructure.web.dto.ChangeStatusRequest;
import com.example.mspedidos.infrastructure.web.dto.HistoryEntryResponse;
import com.example.mspedidos.infrastructure.web.dto.OrderItemResponse;
import com.example.mspedidos.infrastructure.web.dto.OrderLineRequest;
import com.example.mspedidos.infrastructure.web.dto.UpdateItemRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador driver: unidades (items) de una orden. */
@RestController
@RequestMapping("/api/v1/orders/{orderId}/items")
@RequiredArgsConstructor
public class OrderItemController {

    private final OrderItemUseCase itemUseCase;
    private final InventoryAllocationUseCase allocationUseCase;
    private final AllocationUndoUseCase undoUseCase;

    @GetMapping
    public List<OrderItemResponse> findByOrder(@PathVariable Long orderId) {
        return itemUseCase.findByOrder(orderId).stream().map(OrderItemResponse::from).toList();
    }

    /** El cuerpo es una linea de pedido: se expande en {@code quantity} unidades. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<OrderItemResponse> addUnits(@PathVariable Long orderId, @Valid @RequestBody OrderLineRequest request) {
        return itemUseCase.addUnits(orderId, request.toLine()).stream().map(OrderItemResponse::from).toList();
    }

    @GetMapping("/{itemId}")
    public OrderItemResponse findOne(@PathVariable Long orderId, @PathVariable Long itemId) {
        return OrderItemResponse.from(itemUseCase.findOne(orderId, itemId));
    }

    @PutMapping("/{itemId}")
    public OrderItemResponse update(@PathVariable Long orderId, @PathVariable Long itemId,
                                    @Valid @RequestBody UpdateItemRequest request) {
        return OrderItemResponse.from(
                itemUseCase.updateDetails(orderId, itemId, request.sku(), request.externalItemId(), request.unitPrice()));
    }

    @PatchMapping("/{itemId}/status")
    public OrderItemResponse changeStatus(@PathVariable Long orderId, @PathVariable Long itemId,
                                          @Valid @RequestBody ChangeStatusRequest request) {
        return OrderItemResponse.from(itemUseCase.changeStatus(
                orderId, itemId, request.status(), request.warehouse(), request.deliveredTo()));
    }

    @PutMapping("/{itemId}/importer")
    public OrderItemResponse assignImporter(@PathVariable Long orderId, @PathVariable Long itemId,
                                            @Valid @RequestBody AssignImporterRequest request) {
        return OrderItemResponse.from(itemUseCase.assignImporter(orderId, itemId, request.toCommand()));
    }

    @PostMapping("/{itemId}/inventory-allocation")
    public OrderItemResponse allocate(@PathVariable Long orderId, @PathVariable Long itemId,
                                      @Valid @RequestBody AllocateInventoryRequest request) {
        return OrderItemResponse.from(allocationUseCase.allocate(orderId, itemId, request.inventoryUnitId()));
    }

    @PostMapping("/{itemId}/allocation-undo-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AllocationUndoRequestResponse requestUndo(@PathVariable Long orderId, @PathVariable Long itemId,
                                                     @Valid @RequestBody UndoRequestCreateRequest request) {
        return AllocationUndoRequestResponse.from(undoUseCase.request(orderId, itemId, request.reason()));
    }

    @GetMapping("/{itemId}/history")
    public List<HistoryEntryResponse> history(@PathVariable Long orderId, @PathVariable Long itemId) {
        return itemUseCase.history(orderId, itemId).stream().map(HistoryEntryResponse::from).toList();
    }
}
