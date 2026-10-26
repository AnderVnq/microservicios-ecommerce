package com.example.msclientes.application.port.out;

import com.example.msclientes.domain.model.ReservaImportacion;

import java.util.Optional;

public interface ReservaImportacionRepositoryPort {

    Optional<ReservaImportacion> findByIdempotencyKey(String idempotencyKey);

    ReservaImportacion save(ReservaImportacion reserva);
}
