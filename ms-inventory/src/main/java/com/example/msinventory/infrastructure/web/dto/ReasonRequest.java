package com.example.msinventory.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ReasonRequest(@NotBlank String reason) {
}
