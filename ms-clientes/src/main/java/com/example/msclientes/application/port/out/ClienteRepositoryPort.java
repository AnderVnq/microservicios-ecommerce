package com.example.msclientes.application.port.out;

import com.example.msclientes.domain.model.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteRepositoryPort {

    List<Cliente> findAll();

    Optional<Cliente> findById(Long id);

    boolean existsByDocumento(String tipoDocumento, String numeroDocumento);

    Cliente save(Cliente cliente);

    /** Incrementa atomicamente el consumo del cupo solo si hay disponibilidad. True si tuvo exito. */
    boolean reservarUnidadCupo(Long clienteId);

    /** Decrementa atomicamente el consumo del cupo (nunca por debajo de cero). */
    void liberarUnidadCupo(Long clienteId);
}
