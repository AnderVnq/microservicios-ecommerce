package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.Supplier;
import com.example.mspedidos.domain.model.SupplierStatus;

import java.util.List;

public interface SupplierUseCase {

    /** Crea un proveedor ACTIVE; el nombre no puede repetirse (sin distinguir mayusculas). */
    Supplier create(String name, String country, String address, String description);

    /** Actualizacion parcial: un campo {@code null} queda sin cambios. */
    Supplier update(Long id, String name, String country, String address, String description,
                    SupplierStatus status);

    Supplier findOne(Long id);

    List<Supplier> search(SupplierStatus status, String country, String name);
}
