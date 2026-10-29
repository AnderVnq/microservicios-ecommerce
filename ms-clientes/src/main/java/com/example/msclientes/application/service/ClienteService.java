package com.example.msclientes.application.service;

import com.example.msclientes.domain.exception.ClienteAlreadyExistsException;
import com.example.msclientes.domain.exception.ClienteNotFoundException;
import com.example.msclientes.domain.model.Cliente;
import com.example.msclientes.application.port.in.ClienteUseCase;
import com.example.msclientes.application.port.out.ClienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClienteService implements ClienteUseCase {

    private final ClienteRepositoryPort clienteRepository;

    @Override
    public List<Cliente> findAll() {
        return clienteRepository.findAll();
    }

    @Override
    public Cliente findById(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ClienteNotFoundException(id));
    }

    @Override
    @Transactional
    public Cliente create(String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
                           String email, String telefono) {
        if (clienteRepository.existsByDocumento(tipoDocumento, numeroDocumento)) {
            throw new ClienteAlreadyExistsException(numeroDocumento);
        }
        Cliente cliente = Cliente.createNew(nombres, apellidos, tipoDocumento, numeroDocumento,
                email, telefono);
        return clienteRepository.save(cliente);
    }

    @Override
    @Transactional
    public Cliente update(Long id, String nombres, String apellidos, String email, String telefono) {
        Cliente cliente = findById(id);
        cliente.actualizarDatos(nombres, apellidos, email, telefono);
        return clienteRepository.save(cliente);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Cliente cliente = findById(id);
        cliente.desactivar();
        clienteRepository.save(cliente);
    }
}
