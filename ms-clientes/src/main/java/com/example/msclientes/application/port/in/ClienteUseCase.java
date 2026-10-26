package com.example.msclientes.application.port.in;

import com.example.msclientes.domain.model.Cliente;

import java.util.List;

public interface ClienteUseCase {

    List<Cliente> findAll();

    Cliente findById(Long id);

    Cliente create(String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
                    String email, String telefono);

    Cliente update(Long id, String nombres, String apellidos, String email, String telefono);

    void deactivate(Long id);
}
