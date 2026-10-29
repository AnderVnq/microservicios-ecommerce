package com.example.msclientes.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataClienteRepository extends JpaRepository<ClienteJpaEntity, Long> {

    boolean existsByTipoDocumentoAndNumeroDocumento(String tipoDocumento, String numeroDocumento);
}
