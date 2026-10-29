package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.QuotaLockPort;
import com.example.mspedidos.domain.model.ImporterDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuotaLockAdapter.class)
class QuotaLockAdapterTest {

    @Autowired
    QuotaLockPort lockPort;
    @Autowired
    SpringDataImporterQuotaLockRepository locks;

    @Test
    void lock_createsRowOnceAndIsReentrant() {
        ImporterDocument doc = new ImporterDocument("DNI", "reentrant");

        lockPort.lock(doc, 2026);
        lockPort.lock(doc, 2026);

        assertThat(rowsFor("DNI", "reentrant")).hasSize(1);
    }

    @Test
    void lock_usesSeparateRowPerDocumentAndYear() {
        lockPort.lock(new ImporterDocument("DNI", "separate"), 2026);
        lockPort.lock(new ImporterDocument("DNI", "separate"), 2027);
        lockPort.lock(new ImporterDocument("CE", "separate"), 2026);

        assertThat(rowsFor("DNI", "separate")).hasSize(2);
        assertThat(rowsFor("CE", "separate")).hasSize(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lock_outsideTransaction_failsFast() {
        assertThatThrownBy(() -> lockPort.lock(new ImporterDocument("DNI", "no-tx"), 2026))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // el insert REQUIRES_NEW se confirma aunque el test haga rollback: cada test usa su propio documento
    private java.util.List<ImporterQuotaLockJpaEntity> rowsFor(String type, String number) {
        return locks.findAll().stream()
                .filter(l -> l.getDocumentType().equals(type) && l.getDocumentNumber().equals(number))
                .toList();
    }
}
