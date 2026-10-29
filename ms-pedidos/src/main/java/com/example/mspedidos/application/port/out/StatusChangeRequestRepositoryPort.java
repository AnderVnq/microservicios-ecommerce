package com.example.mspedidos.application.port.out;

import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestStatus;
import com.example.mspedidos.domain.model.StatusChangeRequestType;

import java.util.List;
import java.util.Optional;

public interface StatusChangeRequestRepositoryPort {

    StatusChangeRequest save(StatusChangeRequest request);

    Optional<StatusChangeRequest> findById(Long id);

    /** Como {@link #findById} pero con lock pesimista de escritura; requiere transaccion. */
    Optional<StatusChangeRequest> findByIdForUpdate(Long id);

    Optional<StatusChangeRequest> findPendingByOrderItemId(Long orderItemId);

    /** Solicitud creada con esa {@code Idempotency-Key} (cualquier estado). */
    Optional<StatusChangeRequest> findByIdempotencyKey(String idempotencyKey);

    /** Si alguna unidad de la orden tiene una solicitud {@code PENDING}. */
    boolean existsPendingForOrder(Long orderId);

    /** {@code status} null no filtra; mas recientes primero. */
    List<StatusChangeRequest> search(StatusChangeRequestStatus status);

    Optional<StatusChangeRequest> findLatestApproved(Long orderItemId);

    /** Filtra por estado y/o tipo ({@code null} no filtra); mas recientes primero. */
    List<StatusChangeRequest> search(StatusChangeRequestStatus status, StatusChangeRequestType type);

    /** Solicitud {@code PENDING} de "no recibido" de la compra, si existe. */
    Optional<StatusChangeRequest> findPendingByPurchaseId(Long purchaseId);

    /** Suma de {@code quantity} de las solicitudes {@code PENDING} de "no recibido" de la compra. */
    int sumPendingNotReceived(Long purchaseId);

    /** Como {@link #sumPendingNotReceived} sin contar la solicitud {@code excludeRequestId} (null no excluye). */
    int sumPendingNotReceivedExcluding(Long purchaseId, Long excludeRequestId);
}
