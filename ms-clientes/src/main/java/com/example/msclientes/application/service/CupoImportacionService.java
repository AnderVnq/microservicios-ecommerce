package com.example.msclientes.application.service;

import com.example.msclientes.domain.exception.ClienteNotFoundException;
import com.example.msclientes.domain.exception.CupoImportacionAgotadoException;
import com.example.msclientes.domain.model.Cliente;
import com.example.msclientes.domain.model.ReservaImportacion;
import com.example.msclientes.application.port.in.CupoImportacionUseCase;
import com.example.msclientes.application.port.out.ClienteRepositoryPort;
import com.example.msclientes.application.port.out.ReservaImportacionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CupoImportacionService implements CupoImportacionUseCase {

    private final ClienteRepositoryPort clienteRepository;
    private final ReservaImportacionRepositoryPort reservaRepository;

    @Override
    @Transactional(readOnly = true)
    public Cliente consultarCupo(Long clienteId) {
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNotFoundException(clienteId));
    }

    @Override
    @Transactional
    public ReservaImportacion reservar(Long clienteId, Long pedidoId, String idempotencyKey) {
        var existente = reservaRepository.findByIdempotencyKey(idempotencyKey);
        if (existente.isPresent()) {
            return existente.get();
        }

        boolean reservado = clienteRepository.reservarUnidadCupo(clienteId);
        if (!reservado) {
            throw new CupoImportacionAgotadoException(clienteId);
        }

        try {
            return reservaRepository.save(ReservaImportacion.activa(clienteId, pedidoId, idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            // Carrera: otro request con la misma key gano la insercion; revertimos nuestro incremento.
            clienteRepository.liberarUnidadCupo(clienteId);
            return reservaRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional
    public void liberar(String idempotencyKey) {
        reservaRepository.findByIdempotencyKey(idempotencyKey).ifPresent(reserva -> {
            if (reserva.estaActiva()) {
                clienteRepository.liberarUnidadCupo(reserva.getClienteId());
                reserva.liberar();
                reservaRepository.save(reserva);
            }
        });
    }
}
