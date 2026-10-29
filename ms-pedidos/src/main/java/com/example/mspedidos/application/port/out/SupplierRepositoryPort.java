package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.Supplier;
import com.example.mspedidos.domain.model.SupplierStatus;

import java.util.List;
import java.util.Optional;

public interface SupplierRepositoryPort {

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(Long id);

    /** Si ya existe un proveedor con ese nombre (sin distinguir mayusculas); {@code excludeId} puede ser null. */
    boolean existsByNameIgnoreCase(String name, Long excludeId);

    /** Filtros opcionales ({@code null} no filtra); {@code nameContains} sin distinguir mayusculas. Por nombre. */
    List<Supplier> search(SupplierStatus status, String country, String nameContains);
}
