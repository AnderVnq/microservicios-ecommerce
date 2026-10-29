package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.Supplier;
import com.example.mspedidos.domain.model.SupplierStatus;

import java.time.Instant;

public record SupplierResponse(Long id, String name, SupplierStatus status, String country, String address,
                               String description, Instant createdAt, Instant updatedAt) {

    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getStatus(), s.getCountry(), s.getAddress(),
                s.getDescription(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
