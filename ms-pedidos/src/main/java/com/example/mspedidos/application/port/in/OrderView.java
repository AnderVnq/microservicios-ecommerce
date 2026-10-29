package com.example.mspedidos.application.port.in;

import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;

import java.util.List;

public record OrderView(Order order, List<OrderItem> items) {
}
