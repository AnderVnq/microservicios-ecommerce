package com.example.msinventory.application.port.in;

public interface InventoryEntryUseCase {

    /** El hash del cuerpo lo calcula la capa web; misma clave + mismo hash = repeticion (replayed). */
    EntryResult register(String idempotencyKey, String requestHash, RegisterEntryCommand cmd);
}
