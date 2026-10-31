package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.CreateOrderCommand;
import com.example.mspedidos.application.port.in.OrderUseCase;
import com.example.mspedidos.application.port.in.OrderView;
import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.StatusChangeRequestRepositoryPort;
import com.example.mspedidos.application.port.out.ClientesPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.OrderRepositoryPort;
import com.example.mspedidos.domain.exception.DuplicateExternalOrderException;
import com.example.mspedidos.domain.exception.OrderNotFoundException;
import com.example.mspedidos.domain.exception.StatusChangeRequestPendingException;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderHeaderChanges;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.Person;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Orden de locks: orden -> cupo -> unidades por id ascendente (ver {@link OrderItemService}). */
@Service
public class OrderService implements OrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ClientesPort clientesPort;
    private final ActorProvider actorProvider;
    private final UnitCreator unitCreator;
    private final OrderItemRepositoryPort itemRepository;
    private final ItemCanceller canceller;
    private final InventoryReleaser releaser;
    private final StatusChangeRequestRepositoryPort undoRepository;
    private final TransactionTemplate tx;

    OrderService(OrderRepositoryPort orderRepository, ClientesPort clientesPort, ActorProvider actorProvider,
                 UnitCreator unitCreator, OrderItemRepositoryPort itemRepository, ItemCanceller canceller,
                 InventoryReleaser releaser, StatusChangeRequestRepositoryPort undoRepository,
                 PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.clientesPort = clientesPort;
        this.actorProvider = actorProvider;
        this.unitCreator = unitCreator;
        this.itemRepository = itemRepository;
        this.canceller = canceller;
        this.releaser = releaser;
        this.undoRepository = undoRepository;
        this.tx = new TransactionTemplate(transactionManager);
        // READ COMMITTED explicito: con REPEATABLE READ (default de InnoDB) el snapshot se fija en la primera
        // lectura, antes de esperar el lock de cupo, y el conteo posterior no veria las unidades del ganador.
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public OrderView create(CreateOrderCommand cmd) {
        // ms-clientes se consulta antes de abrir la transaccion: nunca mientras se tiene el lock de cupo
        Person holder = clientesPort.getHolder(cmd.holderId());
        String actor = actorProvider.currentActor();
        Map<String, String> resolved = unitCreator.resolveSkus(cmd.marketId(), cmd.lines());
        try {
            return tx.execute(status -> {
                ensureNotDuplicate(cmd);
                Order order = orderRepository.save(newOrder(cmd));
                List<OrderItem> items = unitCreator.createUnits(order, holder.document(), cmd.lines(), resolved, actor);
                order.recalculateItemsTotal(items);
                return new OrderView(orderRepository.save(order), items);
            });
        } catch (DataIntegrityViolationException e) {
            // carrera con otra creacion de la misma orden externa: la constraint unica es la ultima defensa
            ensureNotDuplicate(cmd);
            throw e;
        }
    }

    @Override
    public List<OrderView> findAll() {
        List<Order> orders = orderRepository.findAll();
        Map<Long, List<OrderItem>> itemsByOrder = itemRepository
                .findByOrderIds(orders.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
                .map(o -> new OrderView(o, itemsByOrder.getOrDefault(o.getId(), List.of())))
                .toList();
    }

    @Override
    public OrderView findById(Long id) {
        Order order = findOrder(id);
        return new OrderView(order, itemRepository.findByOrderId(id));
    }

    @Override
    public Order updateHeader(Long id, OrderHeaderChanges changes) {
        return tx.execute(status -> {
            Order order = lockOrder(id);
            order.updateHeader(changes);
            return orderRepository.save(order);
        });
    }

    @Override
    public OrderView cancel(Long id) {
        String actor = actorProvider.currentActor();
        OrderView cancelled = tx.execute(status -> {
            Order order = lockOrder(id);
            List<OrderItem> locked = itemRepository.findByOrderIdForUpdate(id);
            if (undoRepository.existsPendingForOrder(id)) {
                throw new StatusChangeRequestPendingException("La orden " + id
                        + " tiene unidades con una solicitud de cambio de estado pendiente");
            }
            List<OrderItem> items = locked.stream()
                    .map(item -> item.getStatus().isTerminal() ? item : canceller.cancel(item, actor))
                    .toList();
            order.recalculateItemsTotal(items);
            return new OrderView(orderRepository.save(order), items);
        });
        // cancelacion ya confirmada: cada unidad cubierta se libera en ms-inventory fuera de la transaccion; un
        // fallo deja la marca para el job y nunca deshace la cancelacion
        List<OrderItem> items = cancelled.items().stream().map(item -> releaser.release(item, actor)).toList();
        return new OrderView(cancelled.order(), items);
    }

    private Order lockOrder(Long id) {
        return orderRepository.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private void ensureNotDuplicate(CreateOrderCommand cmd) {
        if (orderRepository.existsByMarketIdAndExternalOrderId(cmd.marketId(), cmd.externalOrderId())) {
            throw new DuplicateExternalOrderException(cmd.externalOrderId());
        }
    }

    private static Order newOrder(CreateOrderCommand cmd) {
        return Order.builder()
                .orderNumber(cmd.orderNumber())
                .externalOrderId(cmd.externalOrderId())
                .marketId(cmd.marketId())
                .holderId(cmd.holderId())
                .orderDate(cmd.orderDate())
                .shippingDeadline(cmd.shippingDeadline())
                .usaDepartureDeadline(cmd.usaDepartureDeadline())
                .deliveryDate(cmd.deliveryDate())
                .leadTimeToShip(cmd.leadTimeToShip())
                .shippingMethod(cmd.shippingMethod())
                .itemsTotal(BigDecimal.ZERO)
                .shippingCost(cmd.shippingCost())
                .orderExpenses(cmd.orderExpenses())
                .storeDiscountTotal(cmd.storeDiscountTotal())
                .note(cmd.note())
                .build();
    }
}
