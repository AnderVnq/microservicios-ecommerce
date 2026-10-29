package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.StatusChangeRequest;

/**
 * Resultado de marcar no recibidas: la compra actualizada y la solicitud creada ({@code PENDING}) o aplicada
 * ({@code APPLIED_DIRECTLY}); {@code request} es null cuando la marca fue solo sobrante (aplicada sin solicitud).
 */
public record NotReceivedResult(PurchaseView view, StatusChangeRequest request) {
}
