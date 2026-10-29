package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.infrastructure.web.dto.OrderItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador driver: busqueda transversal de unidades (la ruta literal gana a {@code /orders/{id}}). */
@RestController
@RequestMapping("/api/v1/orders/items")
@RequiredArgsConstructor
public class OrderItemSearchController {

    private final OrderItemUseCase itemUseCase;

    @GetMapping
    public List<OrderItemResponse> search(@RequestParam(required = false) Boolean inventoryReleasePending) {
        return itemUseCase.findByReleasePending(inventoryReleasePending).stream().map(OrderItemResponse::from).toList();
    }
}
