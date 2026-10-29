package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.AllocationUndoRequest;
import com.example.mspedidos.domain.model.UndoRequestStatus;

import java.util.List;

/** Deshacer una cobertura de inventario equivocada: solicitud con motivo + aprobacion de un admin (spec 10.2c). */
public interface AllocationUndoUseCase {

    /** Abre una solicitud {@code PENDING}; la unidad debe seguir como la dejo la asignacion. */
    AllocationUndoRequest request(Long orderId, Long itemId, String reason);

    /** {@code status} null no filtra. */
    List<AllocationUndoRequest> search(UndoRequestStatus status);

    /** Solo un admin distinto del solicitante; deshace la cobertura y libera la unidad de inventario. */
    AllocationUndoRequest approve(Long requestId, String note);

    /** Solo un admin; la nota es obligatoria y la unidad queda igual. */
    AllocationUndoRequest reject(Long requestId, String note);
}
