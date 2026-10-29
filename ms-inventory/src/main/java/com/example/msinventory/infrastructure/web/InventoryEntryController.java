package com.example.msinventory.infrastructure.web;

import com.example.msinventory.application.port.in.EntryResult;
import com.example.msinventory.application.port.in.InventoryEntryUseCase;
import com.example.msinventory.infrastructure.web.dto.EntryResponse;
import com.example.msinventory.infrastructure.web.dto.RegisterEntryRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador driver: entradas de inventario (idempotentes por cabecera Idempotency-Key). */
@RestController
@RequestMapping("/api/v1/inventory/entries")
@RequiredArgsConstructor
public class InventoryEntryController {

    private final InventoryEntryUseCase entries;
    private final RequestHasher hasher;

    @PostMapping
    public ResponseEntity<EntryResponse> register(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  @Valid @RequestBody RegisterEntryRequest request) {
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La cabecera Idempotency-Key es obligatoria");
        }
        EntryResult result = entries.register(idempotencyKey, hasher.hash(request), request.toCommand());
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(EntryResponse.from(result));
    }
}
