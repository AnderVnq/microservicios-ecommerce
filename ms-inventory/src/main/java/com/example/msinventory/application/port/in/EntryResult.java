package com.example.msinventory.application.port.in;

import com.example.msinventory.domain.model.InventoryEntry;
import com.example.msinventory.domain.model.InventoryUnit;

import java.util.List;

public record EntryResult(InventoryEntry entry, List<InventoryUnit> units, List<String> warnings, boolean replayed) {
}
