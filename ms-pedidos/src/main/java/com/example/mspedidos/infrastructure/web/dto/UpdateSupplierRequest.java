package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.domain.model.SupplierStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH: un campo ausente o {@code null} queda sin cambios. */
public record UpdateSupplierRequest(
        @Size(min = 1, max = 150) String name,
        @Pattern(regexp = "[A-Z]{2}") String country,
        @Size(max = 300) String address,
        String description,
        SupplierStatus status) {
}
