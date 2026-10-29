package com.example.msclientes.application.port.out;

import com.example.msclientes.domain.model.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteRepositoryPort {

    List<Cliente> findAll();

    Optional<Cliente> findById(Long id);

    boolean existsByDocumento(String tipoDocumento, String numeroDocumento);

    Cliente save(Cliente cliente);
}
