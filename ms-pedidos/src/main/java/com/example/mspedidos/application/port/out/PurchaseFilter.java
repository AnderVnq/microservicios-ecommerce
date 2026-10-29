package com.example.mspedidos.application.port.out;

/** Filtros opcionales de busqueda de compras ({@code null} no filtra). */
public record PurchaseFilter(Long supplierId, String sku, String invoiceNumber, String supplierPurchaseId,
                             Boolean pending, InventorySyncState inventorySync) {
}
