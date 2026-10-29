package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.ImporterQuotaUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.infrastructure.web.dto.CreateOrderRequest;
import com.example.mspedidos.infrastructure.web.dto.OrderResponse;
import com.example.mspedidos.infrastructure.web.dto.QuotaResponse;
import com.example.mspedidos.infrastructure.web.dto.UpdateOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador driver: traduce HTTP a los puertos de entrada de ordenes y cupo. */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderUseCase orderUseCase;
    private final ImporterQuotaUseCase quotaUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        return OrderResponse.from(orderUseCase.create(request.toCommand()));
    }

    @GetMapping
    public List<OrderResponse> findAll() {
        return orderUseCase.findAll().stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable Long id) {
        return OrderResponse.from(orderUseCase.findById(id));
    }

    @PutMapping("/{id}")
    public OrderResponse update(@PathVariable Long id, @Valid @RequestBody UpdateOrderRequest request) {
        return OrderResponse.from(orderUseCase.updateHeader(id, request.toChanges()));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return OrderResponse.from(orderUseCase.cancel(id));
    }

    @GetMapping("/importer-quota")
    public QuotaResponse quota(@RequestParam Long holderId, @RequestParam int year) {
        return QuotaResponse.from(quotaUseCase.quotaFor(holderId, year));
    }
}
