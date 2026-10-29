package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String country,
        @Size(max = 300) String address,
        String description) {
}
