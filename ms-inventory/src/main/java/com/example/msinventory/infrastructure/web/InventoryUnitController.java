package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.AllocationUseCase;
import com.example.msinventory.application.port.in.InventoryUnitUseCase;
import com.example.msinventory.application.port.out.InventoryUnitRepositoryPort.UnitFilter;
import com.example.msinventory.domain.model.InventoryUnitStatus;
import com.example.msinventory.domain.model.Warehouse;
import com.example.msinventory.infrastructure.web.dto.AllocateRequest;
import com.example.msinventory.infrastructure.web.dto.AllocationResponse;
import com.example.msinventory.infrastructure.web.dto.ArrivalsRequest;
import com.example.msinventory.infrastructure.web.dto.MovementResponse;
import com.example.msinventory.infrastructure.web.dto.ReasonRequest;
import com.example.msinventory.infrastructure.web.dto.SupplierReturnRequest;
import com.example.msinventory.infrastructure.web.dto.TransferRequest;
import com.example.msinventory.infrastructure.web.dto.UnitResponse;
import com.example.msinventory.infrastructure.web.dto.UpdateDatesRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Adaptador driver: operaciones y consultas sobre unidades de inventario. */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryUnitController {

    private final InventoryUnitUseCase units;
    private final AllocationUseCase allocations;

    @GetMapping("/units")
    public List<UnitResponse> search(@RequestParam(required = false) String sku,
                                     @RequestParam(required = false) InventoryUnitStatus status,
                                     @RequestParam(required = false) Warehouse warehouse,
                                     @RequestParam(required = false) String conditionCode,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDeadlineBefore) {
        return units.search(new UnitFilter(sku, status, warehouse, conditionCode, returnDeadlineBefore))
                .stream().map(UnitResponse::from).toList();
    }

    @GetMapping("/units/{id}")
    public UnitResponse findOne(@PathVariable Long id) {
        return UnitResponse.from(units.findOne(id));
    }

    @GetMapping("/units/{id}/movements")
    public List<MovementResponse> movements(@PathVariable Long id) {
        return units.movements(id).stream().map(MovementResponse::from).toList();
    }

    @PostMapping("/units/{id}/arrival")
    public UnitResponse arrive(@PathVariable Long id) {
        return UnitResponse.from(units.arrive(id));
    }

    @PostMapping("/arrivals")
    public List<UnitResponse> arriveAll(@Valid @RequestBody ArrivalsRequest request) {
        return units.arriveAll(request.unitIds()).stream().map(UnitResponse::from).toList();
    }

    @PostMapping("/units/{id}/transfer")
    public UnitResponse transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return UnitResponse.from(units.transfer(id, request.toWarehouse(), request.expectedArrivalDate()));
    }

    @PostMapping("/units/{id}/supplier-return")
    public UnitResponse supplierReturn(@PathVariable Long id, @RequestBody(required = false) SupplierReturnRequest request) {
        return UnitResponse.from(units.supplierReturn(id, request == null ? null : request.reason()));
    }

    @PostMapping("/units/{id}/write-off")
    public UnitResponse writeOff(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return UnitResponse.from(units.writeOff(id, request.reason()));
    }

    @PostMapping("/units/{id}/adjustment-out")
    public UnitResponse adjustOut(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return UnitResponse.from(units.adjustOut(id, request.reason()));
    }

    @PatchMapping("/units/{id}")
    public UnitResponse updateDates(@PathVariable Long id, @RequestBody UpdateDatesRequest request) {
        return UnitResponse.from(units.updateDates(id, request.expectedArrivalDate(), request.supplierReturnDeadline()));
    }

    @PostMapping("/units/{id}/allocation")
    public AllocationResponse allocate(@PathVariable Long id, @Valid @RequestBody AllocateRequest request) {
        return AllocationResponse.from(allocations.allocate(id, request.orderItemId(), request.sku()));
    }

    @DeleteMapping("/units/{id}/allocation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable Long id, @RequestParam Long orderItemId) {
        allocations.release(id, orderItemId);
    }
}
