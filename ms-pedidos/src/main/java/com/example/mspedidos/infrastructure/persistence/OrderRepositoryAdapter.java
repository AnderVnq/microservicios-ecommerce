package com.example.mspedidos.infrastructure.persistence;

import com.example.mspedidos.application.port.out.OrderRepositoryPort;
import com.example.mspedidos.domain.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository jpaRepository;

    @Override
    public Order save(Order order) {
        return toDomain(jpaRepository.save(toEntity(order)));
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Order> findByIdForUpdate(Long id) {
        return jpaRepository.findForUpdate(id).map(this::toDomain);
    }

    @Override
    public List<Order> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByMarketIdAndExternalOrderId(Long marketId, String externalOrderId) {
        return jpaRepository.existsByMarketIdAndExternalOrderId(marketId, externalOrderId);
    }

    private Order toDomain(OrderJpaEntity e) {
        return Order.builder()
                .id(e.getId())
                .orderNumber(e.getOrderNumber())
                .externalOrderId(e.getExternalOrderId())
                .marketId(e.getMarketId())
                .holderId(e.getHolderId())
                .orderDate(e.getOrderDate())
                .shippingDeadline(e.getShippingDeadline())
                .usaDepartureDeadline(e.getUsaDepartureDeadline())
                .deliveryDate(e.getDeliveryDate())
                .leadTimeToShip(e.getLeadTimeToShip())
                .shippingMethod(e.getShippingMethod())
                .itemsTotal(e.getItemsTotal())
                .shippingCost(e.getShippingCost())
                .orderExpenses(e.getOrderExpenses())
                .storeDiscountTotal(e.getStoreDiscountTotal())
                .note(e.getNote())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private OrderJpaEntity toEntity(Order o) {
        return OrderJpaEntity.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .externalOrderId(o.getExternalOrderId())
                .marketId(o.getMarketId())
                .holderId(o.getHolderId())
                .orderDate(o.getOrderDate())
                .shippingDeadline(o.getShippingDeadline())
                .usaDepartureDeadline(o.getUsaDepartureDeadline())
                .deliveryDate(o.getDeliveryDate())
                .leadTimeToShip(o.getLeadTimeToShip())
                .shippingMethod(o.getShippingMethod())
                .itemsTotal(o.getItemsTotal())
                .shippingCost(o.getShippingCost())
                .orderExpenses(o.getOrderExpenses())
                .storeDiscountTotal(o.getStoreDiscountTotal())
                .note(o.getNote())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }
}
