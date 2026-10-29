package com.example.msinventory.infrastructure.web.dto;

import com.example.msinventory.application.port.in.EntryResult;
import com.example.msinventory.domain.model.EntryType;

import java.util.List;

public record EntryResponse(Long entryId, EntryType entryType, int quantity, List<UnitResponse> units,
                            List<String> warnings) {

    public static EntryResponse from(EntryResult r) {
        return new EntryResponse(r.entry().getId(), r.entry().getEntryType(), r.entry().getQuantity(),
                r.units().stream().map(UnitResponse::from).toList(), r.warnings());
    }
}
