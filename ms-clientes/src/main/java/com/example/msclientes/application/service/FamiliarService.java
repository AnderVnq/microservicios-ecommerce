package com.example.msclientes.application.service;

import com.example.msclientes.domain.exception.FamiliarNotFoundException;
import com.example.msclientes.domain.model.Familiar;
import com.example.msclientes.domain.model.Parentesco;
import com.example.msclientes.application.port.in.ClienteUseCase;
import com.example.msclientes.application.port.in.FamiliarUseCase;
import com.example.msclientes.application.port.out.FamiliarRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Un familiar siempre esta asociado a un unico cliente titular y comparte
 * su cupo de importaciones (ver CupoImportacionService).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FamiliarService implements FamiliarUseCase {

    private final FamiliarRepositoryPort familiarRepository;
    private final ClienteUseCase clienteUseCase;

    @Override
    public List<Familiar> findByCliente(Long clienteId) {
        clienteUseCase.findById(clienteId);
        return familiarRepository.findByClienteId(clienteId);
    }

    @Override
    public Familiar findOne(Long clienteId, Long familiarId) {
        return familiarRepository.findByIdAndClienteId(familiarId, clienteId)
                .orElseThrow(() -> new FamiliarNotFoundException(clienteId, familiarId));
    }

    @Override
    @Transactional
    public Familiar create(Long clienteId, String nombres, String apellidos, String tipoDocumento,
                            String numeroDocumento, Parentesco parentesco) {
        clienteUseCase.findById(clienteId);
        Familiar familiar = Familiar.createNew(clienteId, nombres, apellidos, tipoDocumento, numeroDocumento, parentesco);
        return familiarRepository.save(familiar);
    }

    @Override
    @Transactional
    public void deactivate(Long clienteId, Long familiarId) {
        Familiar familiar = findOne(clienteId, familiarId);
        familiar.desactivar();
        familiarRepository.save(familiar);
    }
}
