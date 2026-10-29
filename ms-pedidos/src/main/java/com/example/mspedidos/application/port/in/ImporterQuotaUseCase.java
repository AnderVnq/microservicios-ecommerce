package com.example.mspedidos.application.port.in;

public interface ImporterQuotaUseCase {

    /** Titular primero, luego sus familiares activos; uso contado por documento. */
    QuotaSummary quotaFor(Long holderId, int year);
}
