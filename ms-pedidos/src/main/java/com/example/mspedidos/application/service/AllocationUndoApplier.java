package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.OrderRepositoryPort;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.exception.OrderNotFoundException;
import com.example.mspedidos.domain.exception.UndoNotAllowedException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.ItemIncident;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.StatusChangeRequest;
import com.example.mspedidos.domain.model.StatusChangeRequestType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Deshacer una cobertura de inventario equivocada (spec 10.2c / 8.4). Locks: orden -> unidad (la solicitud la
 * bloquea el servicio al final). La liberacion en ms-inventory va despues del commit via {@link InventoryReleaser}.
 */
@Slf4j
@Component
class AllocationUndoApplier implements StatusChangeApplier {

    private final OrderRepositoryPort orderRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final ItemIncidentRepositoryPort incidentRepository;
    private final ItemHistoryRecorder history;
    private final InventoryReleaser releaser;

    AllocationUndoApplier(OrderRepositoryPort orderRepository, OrderItemRepositoryPort itemRepository,
                          ItemIncidentRepositoryPort incidentRepository, ItemHistoryRecorder history,
                          InventoryReleaser releaser) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.incidentRepository = incidentRepository;
        this.history = history;
        this.releaser = releaser;
    }

    @Override
    public StatusChangeRequestType type() {
        return StatusChangeRequestType.ALLOCATION_UNDO;
    }

    @Override
    public void lockAndValidate(StatusChangeRequest req) {
        OrderItem target = itemOf(req);
        orderRepository.findByIdForUpdate(target.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(target.getOrderId()));
        OrderItem item = itemRepository.findByIdAndOrderIdForUpdate(target.getId(), target.getOrderId())
                .orElseThrow(() -> new OrderItemNotFoundException(target.getId()));
        if (item.getPurchaseId() != null) {
            throw new UndoNotAllowedException("La unidad " + item.getId() + " esta cubierta por la compra "
                    + item.getPurchaseId() + "; se corrige con no recibidas o cancelando");
        }
        if (!item.canUndoAllocation()) {
            throw new UndoNotAllowedException("Solo se puede deshacer la cobertura de inventario mientras la "
                    + "unidad siga como la dejo la asignacion, actual: " + item.getStatus());
        }
        if (!Objects.equals(req.getInventoryUnitId(), item.getInventoryUnitId())) {
            throw new UndoNotAllowedException("La unidad " + item.getId() + " ya no esta cubierta por la unidad "
                    + "de inventario " + req.getInventoryUnitId());
        }
    }

    @Override
    public void apply(StatusChangeRequest req, String actor) {
        // el lock de la unidad ya lo tomo lockAndValidate en esta misma transaccion
        OrderItem item = itemOf(req);
        String fromStatus = item.getStatus().name();
        ImporterType fromImporter = item.getImporterType();
        Long released = item.undoInventoryCoverage();
        OrderItem saved = itemRepository.save(item);
        Long itemId = saved.getId();
        history.record(itemId, HistoryEventType.ALLOCATION_UNDONE, "unit:" + released,
                StatusChangeRequestService.abbreviate("request:" + req.getId() + " by " + actor + ": "
                        + req.getReason()), actor);
        history.record(itemId, HistoryEventType.STATUS_CHANGED, fromStatus, saved.getStatus().name(), actor);
        if (fromImporter != saved.getImporterType()) {
            // simetria con la asignacion desde LIMA (UNASSIGNED/importador -> NOT_REQUIRED)
            history.record(itemId, HistoryEventType.IMPORTER_ASSIGNED, fromImporter.name(),
                    saved.getImporterType().name(), actor);
        }
        if (fromImporter != ImporterType.UNASSIGNED && saved.getImporterType() == ImporterType.UNASSIGNED) {
            incidentRepository.save(ItemIncident.importerPending(itemId,
                    "Asignacion de inventario deshecha; asignar importador", actor));
            history.record(itemId, HistoryEventType.INCIDENT_OPENED, null,
                    IncidentType.IMPORTER_ASSIGNMENT_PENDING.name(), actor);
        }
    }

    /**
     * Deshacer ya confirmado: se libera en ms-inventory fuera de la transaccion; si falla, la marca queda para el job
     * de reintentos y la solicitud sigue resuelta.
     */
    @Override
    public void afterCommit(StatusChangeRequest req, String actor) {
        try {
            itemRepository.findById(req.getOrderItemId()).ifPresent(item -> releaser.release(item, actor));
        } catch (RuntimeException e) {
            log.warn("No se pudo leer la unidad {} para liberar tras la solicitud {}; queda para el job de reintentos",
                    req.getOrderItemId(), req.getId(), e);
        }
    }

    private OrderItem itemOf(StatusChangeRequest req) {
        return itemRepository.findById(req.getOrderItemId())
                .orElseThrow(() -> new OrderItemNotFoundException(req.getOrderItemId()));
    }
}
