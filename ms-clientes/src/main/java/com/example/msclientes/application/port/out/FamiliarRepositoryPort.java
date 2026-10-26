package com.example.msclientes.application.port.out;

import com.example.msclientes.domain.model.Familiar;

import java.util.List;
import java.util.Optional;

public interface FamiliarRepositoryPort {

    List<Familiar> findByClienteId(Long clienteId);

    Optional<Familiar> findByIdAndClienteId(Long id, Long clienteId);

    Familiar save(Familiar familiar);
}
