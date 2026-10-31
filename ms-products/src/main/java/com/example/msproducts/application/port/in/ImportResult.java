package com.example.msproducts.application.port.in;

import java.util.List;

public record ImportResult(int total, int created, int updated, int unchanged, int rejected, List<RowResult> rows) {
}
