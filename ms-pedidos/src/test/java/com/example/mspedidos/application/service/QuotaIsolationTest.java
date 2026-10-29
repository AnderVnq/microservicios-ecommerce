package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * El conteo de cupo debe correr en READ COMMITTED: bajo REPEATABLE READ (MySQL) leeria un snapshot anterior al
 * lock y no veria las unidades confirmadas por quien tenia el lock. H2 no lo reproduce, asi que se fija el nivel.
 */
class QuotaIsolationTest extends ServiceTestSupport {

    @MockitoSpyBean
    OrderItemRepositoryPort spiedItems;
    @Autowired
    OrderUseCase orderUseCase;
    @Autowired
    OrderItemUseCase itemUseCase;

    private final List<Integer> isolationAtCount = new CopyOnWriteArrayList<>();

    @BeforeEach
    void recordIsolationOfQuotaCounts() {
        doAnswer(inv -> {
            isolationAtCount.add(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            return inv.callRealMethod();
        }).when(spiedItems).countQuotaUnits(any(), anyInt(), any());
    }

    @Test
    void quotaCountsRunInReadCommitted_onCreateAddUnitsAndAssign() {
        stubHolder(1L, dni("111"));
        stubFamilyMember(1L, 7L, dni("333"));

        OrderView view = orderUseCase.create(command(1L, uniqueExternalId(), ORDER_DATE, line(4, "10.00")));
        itemUseCase.addUnits(view.order().getId(), line(1, "10.00"));
        OrderItem pending = view.items().stream()
                .filter(i -> i.getImporterType() == ImporterType.UNASSIGNED).findFirst().orElseThrow();
        itemUseCase.assignImporter(view.order().getId(), pending.getId(),
                new AssignImporterCommand(ImporterType.FAMILY_MEMBER, 7L));

        assertThat(isolationAtCount).hasSize(3).containsOnly(Connection.TRANSACTION_READ_COMMITTED);
    }
}
