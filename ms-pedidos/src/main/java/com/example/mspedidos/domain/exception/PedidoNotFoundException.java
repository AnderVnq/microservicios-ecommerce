package com.example.mspedidos.domain.exception;

public class PedidoNotFoundException extends RuntimeException {

    public PedidoNotFoundException(Long id) {
        super("Pedido %d no existe".formatted(id));
    }
}
