package com.example.mspedidos.application.port.in;

import java.util.List;

public record QuotaSummary(int year, int maxUnitsPerYear, List<PersonQuota> persons) {
}
