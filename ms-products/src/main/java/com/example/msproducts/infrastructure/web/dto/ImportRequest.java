package com.example.msproducts.infrastructure.web.dto;

import com.example.msproducts.application.port.in.ImportRow;
import com.example.msproducts.application.port.in.MarketSkuData;
import com.example.msproducts.application.port.in.ProductImportUseCase;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cuerpo de POST /products/import. Solo se valida el tamano de la carga: los datos de
 * cada fila (obligatorios, largos, unidades) los revisa el servicio y rechazan solo esa fila.
 */
public record ImportRequest(@NotNull @Size(max = ProductImportUseCase.MAX_ROWS) List<Row> rows) {

    public List<ImportRow> toRows() {
        return rows.stream().map(r -> r == null ? null : r.toRow()).toList();
    }

    public record Row(String sku, String mainSku, String name, String brand, String description,
                      String categoryPath, String hsCode, String color, String size, String barcode, String asin,
                      WeightDto weight, DimensionsDto dimensions, BigDecimal referencePrice, String priceCurrency,
                      String priceSourceUrl, String imageUrl, List<MarketSkuRequest> marketSkus) {

        ImportRow toRow() {
            return new ImportRow(sku, mainSku, name, brand, description, categoryPath, hsCode, color, size,
                    barcode, asin,
                    weight == null ? null : new ImportRow.RawWeight(weight.value(), weight.unit()),
                    dimensions == null ? null : new ImportRow.RawDimensions(dimensions.length(), dimensions.width(),
                            dimensions.height(), dimensions.unit()),
                    referencePrice, priceCurrency, priceSourceUrl, imageUrl,
                    marketSkus == null ? List.of() : marketSkus.stream()
                            .map(m -> m == null ? null : new MarketSkuData(m.marketId(), m.marketSku())).toList());
        }
    }
}
