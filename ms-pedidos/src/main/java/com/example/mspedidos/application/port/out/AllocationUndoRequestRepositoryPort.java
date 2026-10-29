package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.AllocationUndoRequest;
import com.example.mspedidos.domain.model.UndoRequestStatus;

import java.util.List;
import java.util.Optional;

public interface AllocationUndoRequestRepositoryPort {

    AllocationUndoRequest save(AllocationUndoRequest request);

    Optional<AllocationUndoRequest> findById(Long id);

    /** Como {@link #findById} pero con lock pesimista de escritura; requiere transaccion. */
    Optional<AllocationUndoRequest> findByIdForUpdate(Long id);

    Optional<AllocationUndoRequest> findPendingByOrderItemId(Long orderItemId);

    /** Si alguna unidad de la orden tiene una solicitud {@code PENDING}. */
    boolean existsPendingForOrder(Long orderId);

    /** {@code status} null no filtra; mas recientes primero. */
    List<AllocationUndoRequest> search(UndoRequestStatus status);

    Optional<AllocationUndoRequest> findLatestApproved(Long orderItemId);
}
