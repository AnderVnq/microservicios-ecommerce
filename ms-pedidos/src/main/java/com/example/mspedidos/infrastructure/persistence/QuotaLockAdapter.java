package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.QuotaLockPort;
import com.example.mspedidos.domain.model.ImporterDocument;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class QuotaLockAdapter implements QuotaLockPort {

    private final SpringDataImporterQuotaLockRepository jpaRepository;
    private final TransactionTemplate requiresNew;

    QuotaLockAdapter(SpringDataImporterQuotaLockRepository jpaRepository,
                     PlatformTransactionManager transactionManager) {
        this.jpaRepository = jpaRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(ImporterDocument document, int year) {
        String type = document.type();
        String number = document.number();
        // sondeo sin lock: evita el gap lock de un select-for-update sobre fila ausente
        if (!jpaRepository.existsByDocumentTypeAndDocumentNumberAndQuotaYear(type, number, year)) {
            insertRow(type, number, year);
        }
        // adquisicion real del lock (select ... for update); la fila ya esta confirmada
        jpaRepository.findByDocumentTypeAndDocumentNumberAndQuotaYear(type, number, year)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear la fila de cupo"));
    }

    private void insertRow(String type, String number, int year) {
        try {
            requiresNew.executeWithoutResult(status -> jpaRepository.saveAndFlush(
                    ImporterQuotaLockJpaEntity.builder()
                            .documentType(type).documentNumber(number).quotaYear(year).build()));
        } catch (DataIntegrityViolationException concurrentInsert) {
            // otra transaccion la creo primero; basta con seleccionarla
        }
    }
}
