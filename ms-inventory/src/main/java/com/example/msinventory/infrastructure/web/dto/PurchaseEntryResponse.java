package com.example.msinventory.infrastructure.web.dto;

import com.example.msinventory.application.port.in.PurchaseEntryUseCase.PurchaseEntryResult;
import com.example.msinventory.domain.model.InventoryUnit;

import java.util.List;
import java.util.stream.IntStream;

public record PurchaseEntryResponse(Long entryId, Long purchaseId, List<Reservation> reservations,
                                    List<Long> unitIds) {

    public record Reservation(Long orderItemId, Long unitId) {
    }

    public static PurchaseEntryResponse from(PurchaseEntryResult r) {
        List<Reservation> reservations = IntStream.range(0, r.reservations().size())
                .mapToObj(i -> new Reservation(r.reservations().get(i), r.units().get(i).getId()))
                .toList();
        return new PurchaseEntryResponse(r.entry().getId(), r.entry().getPurchaseId(), reservations,
                r.units().stream().map(InventoryUnit::getId).toList());
    }
}
