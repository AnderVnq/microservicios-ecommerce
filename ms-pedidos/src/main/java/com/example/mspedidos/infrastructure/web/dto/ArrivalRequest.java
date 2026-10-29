package com.example.mspedidos.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ArrivalRequest(@Min(1) @Max(500) int quantity) {
}
