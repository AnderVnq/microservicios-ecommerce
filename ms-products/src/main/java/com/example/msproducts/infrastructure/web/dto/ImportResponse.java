package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.application.port.in.IgnoredDifference;
import com.example.msproducts.application.port.in.ImportResult;
import com.example.msproducts.application.port.in.RowOutcome;

import java.util.List;

public record ImportResponse(int total, int created, int updated, int unchanged, int rejected, List<Row> rows) {

    public record Row(int row, String sku, RowOutcome result, List<String> reasons,
                      List<Difference> ignoredDifferences) {
    }

    public record Difference(String field, String current, String received) {

        static Difference from(IgnoredDifference d) {
            return new Difference(d.field(), d.current(), d.received());
        }
    }

    public static ImportResponse from(ImportResult r) {
        return new ImportResponse(r.total(), r.created(), r.updated(), r.unchanged(), r.rejected(),
                r.rows().stream().map(row -> new Row(row.row(), row.sku(), row.result(), row.reasons(),
                        row.ignoredDifferences().stream().map(Difference::from).toList())).toList());
    }
}
