package com.example.mspedidos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPedidoRepository extends JpaRepository<PedidoJpaEntity, Long> {
}
