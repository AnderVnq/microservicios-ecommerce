package com.example.mspedidos.infrastructure.web.dto;

import com.example.mspedidos.application.port.in.PersonQuota;
import com.example.mspedidos.application.port.in.QuotaSummary;

import java.util.List;

public record QuotaResponse(int year, int maxUnitsPerYear, List<PersonQuotaResponse> persons) {

    public record PersonQuotaResponse(String personType, Long personId, String fullName, String documentType,
                                      String documentNumber, long usedUnits, long availableUnits) {
        static PersonQuotaResponse from(PersonQuota p) {
            return new PersonQuotaResponse(p.personType(), p.personId(), p.fullName(), p.documentType(),
                    p.documentNumber(), p.usedUnits(), p.availableUnits());
        }
    }

    public static QuotaResponse from(QuotaSummary s) {
        return new QuotaResponse(s.year(), s.maxUnitsPerYear(),
                s.persons().stream().map(PersonQuotaResponse::from).toList());
    }
}
