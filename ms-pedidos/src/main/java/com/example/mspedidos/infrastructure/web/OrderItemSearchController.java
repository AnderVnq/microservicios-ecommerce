package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.ProductLinkUseCase;
import com.example.mspedidos.infrastructure.web.dto.OrderItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Adaptador driver: operaciones transversales de unidades (la ruta literal gana a {@code /orders/{id}}). */
@RestController
@RequestMapping("/api/v1/orders/items")
@RequiredArgsConstructor
public class OrderItemSearchController {

    private final OrderItemUseCase itemUseCase;
    private final ProductLinkUseCase productLinks;

    /** Ambos filtros son opcionales y se combinan; sin filtros devuelve todas las unidades. */
    @GetMapping
    public List<OrderItemResponse> search(@RequestParam(required = false) Boolean inventoryReleasePending,
                                          @RequestParam(required = false) Boolean productLinkPending) {
        return itemUseCase.search(inventoryReleasePending, productLinkPending).stream()
                .map(OrderItemResponse::from).toList();
    }

    /** Dispara a mano el enlace de unidades pendientes con el catalogo (el job lo hace cada minuto). */
    @PostMapping("/link-skus")
    public Map<String, Integer> linkSkus() {
        return Map.of("linked", productLinks.linkPending());
    }
}
