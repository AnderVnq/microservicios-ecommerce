package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.AuthorizationPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Descubrimiento de appliers por tipo, sin contexto de Spring. */
class StatusChangeRequestServiceAppliersTest {

    private final StatusChangeRequestRepositoryPort repository = mock(StatusChangeRequestRepositoryPort.class);
    private final AuthorizationPort authorization = mock(AuthorizationPort.class);
    private final ActorProvider actorProvider = mock(ActorProvider.class);
    private final ItemHistoryRecorder history = mock(ItemHistoryRecorder.class);
    private final OrderItemRepositoryPort itemRepository = mock(OrderItemRepositoryPort.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    private StatusChangeRequestService service(List<StatusChangeApplier> appliers) {
        return new StatusChangeRequestService(repository, authorization, actorProvider, history, itemRepository,
                appliers, txManager);
    }

    private static StatusChangeApplier applierOf(StatusChangeRequestType type) {
        StatusChangeApplier applier = mock(StatusChangeApplier.class);
        when(applier.type()).thenReturn(type);
        return applier;
    }

    @Test
    void submit_unknownType_illegalState_beforeAuthOrTx() {
        StatusChangeRequestService service = service(List.of(applierOf(StatusChangeRequestType.ALLOCATION_UNDO)));

        assertThatThrownBy(() -> service.submit(StatusChangeRequest.openNotReceived(1L, 1, "motivo", "op-1")))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(authorization, repository, history, itemRepository, txManager);
    }

    @Test
    void duplicateApplierForType_illegalState() {
        assertThatThrownBy(() -> service(List.of(applierOf(StatusChangeRequestType.ALLOCATION_UNDO),
                applierOf(StatusChangeRequestType.ALLOCATION_UNDO))))
                .isInstanceOf(IllegalStateException.class);
    }
}
