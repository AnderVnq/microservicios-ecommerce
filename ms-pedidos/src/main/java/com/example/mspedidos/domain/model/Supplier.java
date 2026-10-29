package com.example.mspedidos.domain.model;

import com.example.mspedidos.domain.exception.SupplierInactiveException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.regex.Pattern;

/** Proveedor al que se le compran unidades. */
@Getter
@Builder
@AllArgsConstructor
public class Supplier {

    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

    private Long id;
    private String name;
    private SupplierStatus status;
    private String country;
    private String address;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public static Supplier create(String name, String country, String address, String description) {
        Supplier supplier = Supplier.builder().status(SupplierStatus.ACTIVE).build();
        supplier.apply(name, country, address, description);
        return supplier;
    }

    public void update(String name, String country, String address, String description, SupplierStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }
        apply(name, country, address, description);
        this.status = status;
    }

    public void ensureActive() {
        if (status != SupplierStatus.ACTIVE) {
            throw new SupplierInactiveException("El proveedor esta inactivo: " + name);
        }
    }

    private void apply(String name, String country, String address, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio");
        }
        if (country != null && !COUNTRY.matcher(country).matches()) {
            throw new IllegalArgumentException("El pais debe ser un codigo de 2 letras mayusculas");
        }
        this.name = name.trim();
        this.country = country;
        this.address = address;
        this.description = description;
    }
}
