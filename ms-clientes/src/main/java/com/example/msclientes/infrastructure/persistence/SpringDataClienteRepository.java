package com.example.msclientes.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataClienteRepository extends JpaRepository<ClienteJpaEntity, Long> {

    boolean existsByTipoDocumentoAndNumeroDocumento(String tipoDocumento, String numeroDocumento);

    /**
     * Incrementa atomicamente el consumo del cupo solo si hay disponibilidad.
     * Devuelve la cantidad de filas afectadas (0 = sin cupo disponible).
     */
    @Modifying
    @Query("UPDATE ClienteJpaEntity c SET c.importacionesUsadas = c.importacionesUsadas + 1 " +
            "WHERE c.id = :clienteId AND c.importacionesUsadas < c.maxImportaciones")
    int reservarUnidadCupo(@Param("clienteId") Long clienteId);

    @Modifying
    @Query("UPDATE ClienteJpaEntity c SET c.importacionesUsadas = c.importacionesUsadas - 1 " +
            "WHERE c.id = :clienteId AND c.importacionesUsadas > 0")
    int liberarUnidadCupo(@Param("clienteId") Long clienteId);
}
