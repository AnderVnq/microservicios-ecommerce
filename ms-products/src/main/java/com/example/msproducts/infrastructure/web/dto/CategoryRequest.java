package com.example.msproducts.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** parentId solo aplica al crear; active solo al actualizar (null conserva el valor actual). */
public record CategoryRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        Long parentId,

        Boolean active
) {
}
