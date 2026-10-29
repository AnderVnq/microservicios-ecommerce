package com.example.mspedidos.application.port.in;

public record PersonQuota(String personType, Long personId, String fullName, String documentType,
                          String documentNumber, long usedUnits, long availableUnits) {
}
