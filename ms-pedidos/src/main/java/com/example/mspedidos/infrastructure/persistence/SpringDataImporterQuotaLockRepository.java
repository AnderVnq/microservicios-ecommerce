package com.example.mspedidos.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

interface SpringDataImporterQuotaLockRepository extends JpaRepository<ImporterQuotaLockJpaEntity, Long> {

    /** Sondeo sin lock: un select-for-update sobre fila ausente toma un gap lock en InnoDB. */
    boolean existsByDocumentTypeAndDocumentNumberAndQuotaYear(
            String documentType, String documentNumber, int quotaYear);

    /** Adquisicion del lock (select ... for update). No eliminar aunque parezca redundante. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ImporterQuotaLockJpaEntity> findByDocumentTypeAndDocumentNumberAndQuotaYear(
            String documentType, String documentNumber, int quotaYear);
}
