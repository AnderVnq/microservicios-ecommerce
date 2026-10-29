package com.example.msinventory.infrastructure.web.dto;

import java.time.LocalDate;

public record UpdateDatesRequest(LocalDate expectedArrivalDate, LocalDate supplierReturnDeadline) {
}
