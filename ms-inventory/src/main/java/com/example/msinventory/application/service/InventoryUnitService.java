package com.example.msinventory.application.service;

import com.example.msinventory.application.port.in.InventoryUnitUseCase;
import com.example.msinventory.application.port.out.ActorProvider;
import com.example.msinventory.application.port.out.InventoryMovementRepositoryPort;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort.UnitFilter;
import com.example.msinventory.domain.exception.UnitNotFoundException;
import com.example.msinventory.domain.model.InventoryMovement;
import com.example.msinventory.domain.model.InventoryUnit;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.MovementType;
import com.example.msinventory.domain.model.Warehouse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryUnitService implements InventoryUnitUseCase {

    private final InventoryUnitRepositoryPort units;
    private final InventoryMovementRepositoryPort movements;
    private final MovementRecorder recorder;
    private final ActorProvider actorProvider;
    private final TransactionTemplate tx;

    public InventoryUnitService(InventoryUnitRepositoryPort units, InventoryMovementRepositoryPort movements,
                                MovementRecorder recorder, ActorProvider actorProvider,
                                PlatformTransactionManager transactionManager) {
        this.units = units;
        this.movements = movements;
        this.recorder = recorder;
        this.actorProvider = actorProvider;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public InventoryUnit arrive(Long id) {
        return tx.execute(status -> {
            InventoryUnit unit = lock(id);
            if (unit.arrive()) {
                units.save(unit);
                recorder.record(unit, MovementType.ARRIVAL, null, unit.getWarehouse(), null, null,
                        actorProvider.currentActor());
            }
            return unit;
        });
    }

    @Override
    public List<InventoryUnit> arriveAll(List<Long> ids) {
        List<InventoryUnit> result = new ArrayList<>();
        RuntimeException firstError = null;
        for (Long id : ids) {
            try {
                result.add(arrive(id));
            } catch (RuntimeException e) {
                if (firstError == null) {
                    firstError = e;
                }
            }
        }
        if (firstError != null) {
            throw firstError;
        }
        return result;
    }

    @Override
    public InventoryUnit transfer(Long id, Warehouse to, LocalDate expectedArrivalDate) {
        return tx.execute(status -> {
            InventoryUnit unit = lock(id);
            Warehouse origin = unit.transferTo(to, expectedArrivalDate);
            units.save(unit);
            recorder.record(unit, MovementType.TRANSFER, origin, to, null, null, actorProvider.currentActor());
            return unit;
        });
    }

    @Override
    public InventoryUnit supplierReturn(Long id, String reason) {
        return exit(id, InventoryUnitStatus.RETURNED_TO_SUPPLIER, MovementType.EXIT_SUPPLIER_RETURN, reason);
    }

    @Override
    public InventoryUnit writeOff(Long id, String reason) {
        requireReason(reason);
        return exit(id, InventoryUnitStatus.WRITTEN_OFF, MovementType.EXIT_WRITE_OFF, reason);
    }

    @Override
    public InventoryUnit adjustOut(Long id, String reason) {
        requireReason(reason);
        return exit(id, InventoryUnitStatus.ADJUSTED_OUT, MovementType.EXIT_ADJUSTMENT, reason);
    }

    @Override
    public InventoryUnit updateDates(Long id, LocalDate expectedArrivalDate, LocalDate supplierReturnDeadline) {
        return tx.execute(status -> {
            InventoryUnit unit = lock(id);
            // Semantica PATCH: un campo null/omitido significa "sin cambio".
            unit.updateDates(
                    expectedArrivalDate != null ? expectedArrivalDate : unit.getExpectedArrivalDate(),
                    supplierReturnDeadline != null ? supplierReturnDeadline : unit.getSupplierReturnDeadline());
            return units.save(unit);
        });
    }

    @Override
    public InventoryUnit findOne(Long id) {
        return units.findById(id).orElseThrow(() -> notFound(id));
    }

    @Override
    public List<InventoryUnit> search(UnitFilter filter) {
        return units.search(filter);
    }

    @Override
    public List<InventoryMovement> movements(Long id) {
        findOne(id);
        return movements.findByUnitId(id);
    }

    private InventoryUnit exit(Long id, InventoryUnitStatus target, MovementType type, String reason) {
        return tx.execute(status -> {
            InventoryUnit unit = lock(id);
            if (unit.exit(target)) {
                units.save(unit);
                recorder.record(unit, type, unit.getWarehouse(), null, null, reason, actorProvider.currentActor());
            }
            return unit;
        });
    }

    private InventoryUnit lock(Long id) {
        return units.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static UnitNotFoundException notFound(Long id) {
        return new UnitNotFoundException("Unidad no encontrada: " + id);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
    }
}
