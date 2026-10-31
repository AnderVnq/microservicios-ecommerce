package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.CreateOrderCommand;
import com.example.mspedidos.application.port.in.OrderLine;
import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.ClientesPort;
import com.example.mspedidos.application.port.out.ProductsPort;
import com.example.mspedidos.application.port.out.InventoryPort;
import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemHistoryRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.OrderRepositoryPort;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Person;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Base de los tests de servicio: contexto real sobre H2, sin {@code @Transactional} (cada operacion confirma
 * de verdad), con ms-clientes, ms-inventory y el actor mockeados. Las tablas se limpian antes de cada test.
 * Los jobs de liberaciones y de sincronizacion quedan con un intervalo enorme para que no corran en paralelo a los tests.
 */
@SpringBootTest(properties = {"orders.inventory-release-job.delay-ms=86400000",
        "orders.inventory-sync-job.delay-ms=86400000", "orders.product-link-job.delay-ms=86400000"})
abstract class ServiceTestSupport {

    static final int YEAR = 2026;
    static final LocalDateTime ORDER_DATE = LocalDateTime.of(YEAR, 3, 15, 10, 0);
    private static final AtomicLong SEQ = new AtomicLong();

    @MockitoBean
    ClientesPort clientesPort;
    @MockitoBean
    ActorProvider actorProvider;
    @MockitoBean
    InventoryPort inventoryPort;
    @MockitoBean
    ProductsPort productsPort;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    OrderRepositoryPort orderRepository;
    @Autowired
    OrderItemRepositoryPort itemRepository;
    @Autowired
    OrderItemHistoryRepositoryPort historyRepository;
    @Autowired
    ItemIncidentRepositoryPort incidentRepository;

    @BeforeEach
    void cleanDatabaseAndStubActor() {
        jdbc.update("delete from status_change_requests");
        jdbc.update("delete from order_item_history");
        jdbc.update("delete from item_incidents");
        jdbc.update("delete from order_items");
        jdbc.update("delete from orders");
        jdbc.update("delete from suppliers");
        when(actorProvider.currentActor()).thenReturn("op-1");
        // por defecto el catalogo es la identidad: cada SKU del market existe y resuelve a si mismo
        when(productsPort.resolve(any(), any())).thenAnswer(inv -> {
            Map<String, String> identity = new HashMap<>();
            Collection<String> inputs = inv.getArgument(1);
            if (inputs != null) {
                inputs.forEach(s -> identity.put(s, s));
            }
            return identity;
        });
        // y cada SKU interno tecleado existe con esa misma escritura (mapa identidad)
        when(productsPort.canonical(any())).thenAnswer(inv -> {
            Map<String, String> identity = new HashMap<>();
            Collection<String> inputs = inv.getArgument(0);
            if (inputs != null) {
                inputs.forEach(s -> identity.put(s, s));
            }
            return identity;
        });
    }

    static ImporterDocument dni(String number) {
        return new ImporterDocument("DNI", number);
    }

    Person stubHolder(long holderId, ImporterDocument doc) {
        Person p = new Person(holderId, "Titular " + holderId, doc, true);
        when(clientesPort.getHolder(holderId)).thenReturn(p);
        return p;
    }

    Person stubFamilyMember(long holderId, long familyMemberId, ImporterDocument doc) {
        Person p = new Person(familyMemberId, "Familiar " + familyMemberId, doc, true);
        when(clientesPort.getFamilyMember(eq(holderId), eq(familyMemberId))).thenReturn(p);
        return p;
    }

    static CreateOrderCommand command(long holderId, String externalOrderId, LocalDateTime orderDate,
                                      OrderLine... lines) {
        return new CreateOrderCommand("ORD-" + externalOrderId, externalOrderId, 1L, holderId, orderDate,
                orderDate.plusDays(5), orderDate.plusDays(7), null, 3, "AIR",
                new BigDecimal("10.00"), new BigDecimal("2.00"), BigDecimal.ZERO, null, List.of(lines));
    }

    static OrderLine line(int quantity, String unitPrice) {
        return new OrderLine("SKU-1", "EXT-ITEM-1", quantity, new BigDecimal(unitPrice));
    }

    static String uniqueExternalId() {
        return "EXT-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID();
    }

    /** Persiste una orden de otro cliente con {@code count} unidades que ya consumen cupo de {@code doc}. */
    List<OrderItem> seedUsedUnits(ImporterDocument doc, ImporterType type, LocalDateTime orderDate, int count) {
        Order order = seedOrder(900L, orderDate);
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(OrderItem.builder().orderId(order.getId()).sku("SEED").externalItemId("SEED")
                    .unitPrice(BigDecimal.ONE).status(OrderItemStatus.PROCESSING).importerType(type)
                    .familyMemberId(type == ImporterType.FAMILY_MEMBER ? 99L : null)
                    .importerDocument(doc).build());
        }
        return itemRepository.saveAll(items);
    }

    /** Persiste una orden con {@code count} unidades {@code UNASSIGNED} (sin incidencias). */
    List<OrderItem> seedUnassignedUnits(long holderId, LocalDateTime orderDate, int count) {
        Order order = seedOrder(holderId, orderDate);
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OrderItem unit = OrderItem.newUnit("SKU-U", "SKU-U", "EXT-U", BigDecimal.TEN);
            items.add(OrderItem.builder().orderId(order.getId()).sku(unit.getSku()).marketSku(unit.getMarketSku())
                    .externalItemId(unit.getExternalItemId()).unitPrice(unit.getUnitPrice())
                    .status(unit.getStatus()).importerType(unit.getImporterType()).build());
        }
        return itemRepository.saveAll(items);
    }

    Order seedOrder(long holderId, LocalDateTime orderDate) {
        return orderRepository.save(Order.builder().orderNumber("SEED").externalOrderId(uniqueExternalId())
                .marketId(1L).holderId(holderId).orderDate(orderDate).itemsTotal(BigDecimal.ZERO).build());
    }

    long historyCount(Long itemId, HistoryEventType type) {
        return historyRepository.findByOrderItemId(itemId).stream().filter(h -> h.eventType() == type).count();
    }

    long orderCount() {
        return jdbc.queryForObject("select count(*) from orders", Long.class);
    }

    long itemCount() {
        return jdbc.queryForObject("select count(*) from order_items", Long.class);
    }

    /** Deja la unidad en PURCHASED sin compra asociada (el PATCH manual a PURCHASED ya no existe). */
    protected void forcePurchased(Long itemId) {
        jdbc.update("update order_items set status = 'PURCHASED' where id = ?", itemId);
    }
}
