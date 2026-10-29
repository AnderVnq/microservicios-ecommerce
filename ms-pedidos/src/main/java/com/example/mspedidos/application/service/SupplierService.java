package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.SupplierUseCase;
import com.example.mspedidos.application.port.out.SupplierRepositoryPort;
import com.example.mspedidos.domain.exception.SupplierNameTakenException;
import com.example.mspedidos.domain.exception.SupplierNotFoundException;
import com.example.mspedidos.domain.model.Supplier;
import com.example.mspedidos.domain.model.SupplierStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class SupplierService implements SupplierUseCase {

    private final SupplierRepositoryPort supplierRepository;
    private final TransactionTemplate tx;

    SupplierService(SupplierRepositoryPort supplierRepository, PlatformTransactionManager transactionManager) {
        this.supplierRepository = supplierRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public Supplier create(String name, String country, String address, String description) {
        Supplier supplier = Supplier.create(name, country, address, description);
        return saveEnsuringUniqueName(supplier, null);
    }

    @Override
    public Supplier update(Long id, String name, String country, String address, String description,
                           SupplierStatus status) {
        try {
            return tx.execute(txStatus -> {
                Supplier supplier = supplierRepository.findById(id)
                        .orElseThrow(() -> new SupplierNotFoundException(id));
                supplier.update(
                        name != null ? name : supplier.getName(),
                        country != null ? country : supplier.getCountry(),
                        address != null ? address : supplier.getAddress(),
                        description != null ? description : supplier.getDescription(),
                        status != null ? status : supplier.getStatus());
                ensureNameFree(supplier.getName(), id);
                return supplierRepository.save(supplier);
            });
        } catch (DataIntegrityViolationException e) {
            ensureNameFree(name, id);
            throw e;
        }
    }

    @Override
    public Supplier findOne(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> new SupplierNotFoundException(id));
    }

    @Override
    public List<Supplier> search(SupplierStatus status, String country, String name) {
        return supplierRepository.search(status, country, name);
    }

    private Supplier saveEnsuringUniqueName(Supplier supplier, Long excludeId) {
        try {
            return tx.execute(txStatus -> {
                ensureNameFree(supplier.getName(), excludeId);
                return supplierRepository.save(supplier);
            });
        } catch (DataIntegrityViolationException e) {
            // carrera con otra creacion del mismo nombre: la constraint unica es la ultima defensa
            ensureNameFree(supplier.getName(), excludeId);
            throw e;
        }
    }

    private void ensureNameFree(String name, Long excludeId) {
        if (name != null && supplierRepository.existsByNameIgnoreCase(name, excludeId)) {
            throw new SupplierNameTakenException("Ya existe un proveedor con el nombre: " + name);
        }
    }
}
