package com.example.msclientes.application.port.in;

import com.example.msclientes.domain.model.Familiar;
import com.example.msclientes.domain.model.Parentesco;

import java.util.List;

public interface FamiliarUseCase {

    List<Familiar> findByCliente(Long clienteId);

    Familiar findOne(Long clienteId, Long familiarId);

    Familiar create(Long clienteId, String nombres, String apellidos, String tipoDocumento,
                     String numeroDocumento, Parentesco parentesco);

    void deactivate(Long clienteId, Long familiarId);
}
