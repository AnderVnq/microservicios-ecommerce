package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.AllocationUseCase;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort;
import com.example.msinventory.domain.exception.AllocationConflictException;
import com.example.msinventory.domain.exception.UnitNotFoundException;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.MovementType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AllocationService implements AllocationUseCase {

    private final InventoryUnitRepositoryPort units;
    private final MovementRecorder recorder;
    private final ActorProvider actorProvider;
    private final TransactionTemplate tx;

    public AllocationService(InventoryUnitRepositoryPort units, MovementRecorder recorder,
                             ActorProvider actorProvider, PlatformTransactionManager transactionManager) {
        this.units = units;
        this.recorder = recorder;
        this.actorProvider = actorProvider;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public InventoryUnit allocate(Long unitId, Long orderItemId, String sku) {
        try {
            return tx.execute(status -> doAllocate(unitId, orderItemId, sku));
        } catch (DataIntegrityViolationException e) {
            // Tras el rollback: solo es conflicto si otra unidad realmente cubre el item (restriccion unica);
            // cualquier otro fallo de integridad es un error real y se propaga tal cual.
            InventoryUnit covering = units.findByAllocatedOrderItemId(orderItemId).orElse(null);
            if (covering != null) {
                AllocationConflictException conflict = alreadyCovered(orderItemId, covering.getId());
                conflict.initCause(e);
                throw conflict;
            }
            throw e;
        }
    }

    @Override
    public void release(Long unitId, Long orderItemId) {
        tx.executeWithoutResult(status -> {
            InventoryUnit unit = lock(unitId);
            if (unit.getStatus() == InventoryUnitStatus.AVAILABLE && unit.getAllocatedOrderItemId() == null) {
                return;
            }
            unit.release(orderItemId);
            units.save(unit);
            recorder.record(unit, MovementType.ALLOCATION_RELEASED, null, unit.getWarehouse(),
                    reference(orderItemId), null, actorProvider.currentActor());
        });
    }

    private InventoryUnit doAllocate(Long unitId, Long orderItemId, String sku) {
        InventoryUnit unit = lock(unitId);
        if (unit.getStatus() == InventoryUnitStatus.ALLOCATED) {
            if (orderItemId.equals(unit.getAllocatedOrderItemId())) {
                return unit;
            }
            throw new AllocationConflictException("La unidad " + unitId + " ya esta asignada a otro item de pedido");
        }
        if (unit.getStatus() != InventoryUnitStatus.AVAILABLE) {
            throw new AllocationConflictException("La unidad " + unitId + " no esta disponible (" + unit.getStatus() + ")");
        }
        unit.allocate(orderItemId, sku); // SkuMismatchException si el SKU difiere
        Long coveringUnitId = units.findByAllocatedOrderItemId(orderItemId)
                .map(InventoryUnit::getId).filter(otherId -> !otherId.equals(unitId)).orElse(null);
        if (coveringUnitId != null) {
            throw alreadyCovered(orderItemId, coveringUnitId);
        }
        units.save(unit);
        recorder.record(unit, MovementType.EXIT_ORDER_ALLOCATION, unit.getWarehouse(), null,
                reference(orderItemId), null, actorProvider.currentActor());
        return unit;
    }

    private InventoryUnit lock(Long unitId) {
        return units.findByIdForUpdate(unitId)
                .orElseThrow(() -> new UnitNotFoundException("Unidad no encontrada: " + unitId));
    }

    private static AllocationConflictException alreadyCovered(Long orderItemId, Long coveringUnitId) {
        return new AllocationConflictException(
                "El item de pedido " + orderItemId + " ya esta cubierto por la unidad " + coveringUnitId);
    }

    private static String reference(Long orderItemId) {
        return "order-item:" + orderItemId;
    }
}
