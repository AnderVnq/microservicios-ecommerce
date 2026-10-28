package com.example.ms_auth.infrastructure.web;

import com.example.ms_auth.application.port.in.AutorizacionUseCase;
import com.example.ms_auth.infrastructure.web.dto.AutorizacionResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo consultan el api-gateway o cada microservicio (Opcion A de la guia)
 * antes de permitir una accion. subject es el "sub" (usuario) o "azp"
 * (aplicacion externa) del JWT validado.
 */
@RestController
@RequestMapping("/api/v1/autorizacion")
@RequiredArgsConstructor
@Validated
public class AutorizacionController {

    private final AutorizacionUseCase autorizacionUseCase;

    @GetMapping
    public AutorizacionResponse evaluar(@RequestParam @NotBlank String subject,
                                         @RequestParam @NotBlank String recurso,
                                         @RequestParam @NotBlank String accion) {
        boolean permitido = autorizacionUseCase.evaluar(subject, recurso, accion);
        return new AutorizacionResponse(subject, recurso, accion, permitido);
    }
}
