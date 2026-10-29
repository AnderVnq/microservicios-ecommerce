package com.example.mspedidos.infrastructure.web;

import com.example.mspedidos.application.port.in.SupplierUseCase;
import com.example.mspedidos.domain.model.SupplierStatus;
import com.example.mspedidos.infrastructure.web.dto.CreateSupplierRequest;
import com.example.mspedidos.infrastructure.web.dto.SupplierResponse;
import com.example.mspedidos.infrastructure.web.dto.UpdateSupplierRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Adaptador driver: proveedores. */
@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierUseCase supplierUseCase;

    @GetMapping
    public List<SupplierResponse> search(@RequestParam(required = false) SupplierStatus status,
                                         @RequestParam(required = false) String country,
                                         @RequestParam(required = false) String name) {
        return supplierUseCase.search(status, country, name).stream().map(SupplierResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(@Valid @RequestBody CreateSupplierRequest request) {
        return SupplierResponse.from(supplierUseCase.create(
                request.name(), request.country(), request.address(), request.description()));
    }

    @GetMapping("/{id}")
    public SupplierResponse findOne(@PathVariable Long id) {
        return SupplierResponse.from(supplierUseCase.findOne(id));
    }

    @PatchMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSupplierRequest request) {
        return SupplierResponse.from(supplierUseCase.update(id, request.name(), request.country(),
                request.address(), request.description(), request.status()));
    }
}
