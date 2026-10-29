package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.AssignImporterCommand;
import com.example.mspedidos.application.port.in.OrderItemUseCase;
import com.example.mspedidos.application.port.in.OrderLine;
import com.example.mspedidos.application.port.out.ActorProvider;
import com.example.mspedidos.application.port.out.AllocationUndoRequestRepositoryPort;
import com.example.mspedidos.application.port.out.ClientesPort;
import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemHistoryRepositoryPort;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.OrderRepositoryPort;
import com.example.mspedidos.application.port.out.QuotaLockPort;
import com.example.mspedidos.domain.exception.ImporterChangeNotAllowedException;
import com.example.mspedidos.domain.exception.OrderItemNotFoundException;
import com.example.mspedidos.domain.exception.OrderNotFoundException;
import com.example.mspedidos.domain.exception.QuotaExceededException;
import com.example.mspedidos.domain.exception.UndoRequestPendingException;
import com.example.mspedidos.domain.model.DeliveredTo;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.ImporterDocument;
import com.example.mspedidos.domain.model.ImporterType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.Order;
import com.example.mspedidos.domain.model.OrderItem;
import com.example.mspedidos.domain.model.OrderItemHistoryEntry;
import com.example.mspedidos.domain.model.OrderItemStatus;
import com.example.mspedidos.domain.model.Person;
import com.example.mspedidos.domain.model.Warehouse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

/**
 * Orden de locks (unico y aciclico, igual en todos los caminos, incluido {@link OrderService}):
 * orden -> cupo (documento, anio) -> unidades por id ascendente. Cada operacion toma solo el subconjunto que
 * necesita pero siempre en ese orden. Toda escritura de una unidad lee antes su fila con lock; todo recalculo de
 * {@code items_total} o cambio de cabecera lee antes la orden con lock.
 */
@Service
public class OrderItemService implements OrderItemUseCase {

    private final OrderRepositoryPort orderRepository;
    private final OrderItemRepositoryPort itemRepository;
    private final ItemIncidentRepositoryPort incidentRepository;
    private final ClientesPort clientesPort;
    private final ActorProvider actorProvider;
    private final QuotaLockPort quotaLock;
    private final QuotaAllocator quotaAllocator;
    private final UnitCreator unitCreator;
    private final ItemHistoryRecorder history;
    private final ItemCanceller canceller;
    private final InventoryReleaser releaser;
    private final OrderItemHistoryRepositoryPort historyRepository;
    private final AllocationUndoRequestRepositoryPort undoRepository;
    private final TransactionTemplate tx;

    OrderItemService(OrderRepositoryPort orderRepository, OrderItemRepositoryPort itemRepository,
                     ItemIncidentRepositoryPort incidentRepository, ClientesPort clientesPort,
                     ActorProvider actorProvider, QuotaLockPort quotaLock, QuotaAllocator quotaAllocator,
                     UnitCreator unitCreator, ItemHistoryRecorder history,
                     ItemCanceller canceller, InventoryReleaser releaser,
                     OrderItemHistoryRepositoryPort historyRepository,
                     AllocationUndoRequestRepositoryPort undoRepository,
                     PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.incidentRepository = incidentRepository;
        this.clientesPort = clientesPort;
        this.actorProvider = actorProvider;
        this.quotaLock = quotaLock;
        this.quotaAllocator = quotaAllocator;
        this.unitCreator = unitCreator;
        this.history = history;
        this.canceller = canceller;
        this.releaser = releaser;
        this.historyRepository = historyRepository;
        this.undoRepository = undoRepository;
        this.tx = new TransactionTemplate(transactionManager);
        // READ COMMITTED explicito: con REPEATABLE READ (default de InnoDB) el snapshot se fija en la primera
        // lectura, antes de esperar el lock de cupo, y el conteo posterior no veria las unidades del ganador.
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public List<OrderItem> addUnits(Long orderId, OrderLine line) {
        Person holder = clientesPort.getHolder(findOrder(orderId).getHolderId());
        String actor = actorProvider.currentActor();
        return tx.execute(status -> {
            Order order = lockOrder(orderId);
            List<OrderItem> added = unitCreator.createUnits(order, holder.document(), List.of(line), actor);
            order.recalculateItemsTotal(itemRepository.findByOrderId(orderId));
            orderRepository.save(order);
            return added;
        });
    }

    @Override
    public OrderItem assignImporter(Long orderId, Long itemId, AssignImporterCommand cmd) {
        ImporterType type = cmd.importerType();
        if (type != ImporterType.HOLDER && type != ImporterType.FAMILY_MEMBER) {
            throw new ImporterChangeNotAllowedException("Solo se puede asignar HOLDER o FAMILY_MEMBER");
        }
        if (type == ImporterType.FAMILY_MEMBER && cmd.familyMemberId() == null) {
            throw new ImporterChangeNotAllowedException("familyMemberId es obligatorio para FAMILY_MEMBER");
        }
        Order order = findOrder(orderId);
        // ms-clientes se consulta antes de abrir la transaccion: nunca mientras se tiene el lock de cupo
        Person person = type == ImporterType.HOLDER
                ? clientesPort.getHolder(order.getHolderId())
                : clientesPort.getFamilyMember(order.getHolderId(), cmd.familyMemberId());
        ImporterDocument doc = person.document();
        int year = order.quotaYear();
        String actor = actorProvider.currentActor();

        return tx.execute(status -> {
            quotaLock.lock(doc, year);
            OrderItem item = lockItem(orderId, itemId);
            ensureNoPendingUndo(itemId);
            String from = ItemHistoryRecorder.importerLabel(item);
            if (type == ImporterType.HOLDER) {
                item.assignHolder(doc);
            } else {
                item.assignFamilyMember(person.id(), doc);
            }
            if (quotaAllocator.available(doc, year, itemId) < 1) {
                throw new QuotaExceededException("El documento " + doc.type() + " " + doc.number()
                        + " no tiene cupo disponible en " + year);
            }
            OrderItem saved = itemRepository.save(item);
            history.record(itemId, HistoryEventType.IMPORTER_ASSIGNED, from,
                    ItemHistoryRecorder.importerLabel(saved), actor);
            incidentRepository.findOpen(itemId, IncidentType.IMPORTER_ASSIGNMENT_PENDING).ifPresent(incident -> {
                incident.resolve(actor);
                incidentRepository.save(incident);
                history.record(itemId, HistoryEventType.INCIDENT_RESOLVED,
                        IncidentType.IMPORTER_ASSIGNMENT_PENDING.name(), null, actor);
            });
            return saved;
        });
    }

    @Override
    public List<OrderItem> findByReleasePending(Boolean inventoryReleasePending) {
        return itemRepository.search(inventoryReleasePending);
    }

    @Override
    public List<OrderItem> findByOrder(Long orderId) {
        findOrder(orderId);
        return itemRepository.findByOrderId(orderId);
    }

    @Override
    public OrderItem findOne(Long orderId, Long itemId) {
        return findItem(orderId, itemId);
    }

    @Override
    public OrderItem updateDetails(Long orderId, Long itemId, String sku, String externalItemId,
                                   BigDecimal unitPrice) {
        String actor = actorProvider.currentActor();
        return tx.execute(status -> {
            Order order = lockOrder(orderId);
            OrderItem item = lockItem(orderId, itemId);
            ensureNoPendingUndo(itemId);
            String before = describe(item);
            item.updateDetails(sku, externalItemId, unitPrice);
            OrderItem saved = itemRepository.save(item);
            history.record(itemId, HistoryEventType.UPDATED, before, describe(saved), actor);
            order.recalculateItemsTotal(itemRepository.findByOrderId(orderId));
            orderRepository.save(order);
            return saved;
        });
    }

    @Override
    public OrderItem changeStatus(Long orderId, Long itemId, OrderItemStatus next, Warehouse warehouse,
                                  DeliveredTo deliveredTo) {
        String actor = actorProvider.currentActor();
        OrderItem result = tx.execute(status -> {
            Order order = lockOrder(orderId);
            OrderItem item = lockItem(orderId, itemId);
            ensureNoPendingUndo(itemId);
            OrderItem saved;
            if (next == OrderItemStatus.CANCELLED) {
                saved = canceller.cancel(item, actor);
                order.recalculateItemsTotal(itemRepository.findByOrderId(orderId));
                orderRepository.save(order);
            } else {
                String from = item.getStatus().name();
                item.changeStatus(next, warehouse, deliveredTo);
                saved = itemRepository.save(item);
                history.record(itemId, HistoryEventType.STATUS_CHANGED, from, next.name(), actor);
            }
            return saved;
        });
        // cancelacion ya confirmada: se libera la unidad de inventario fuera de la transaccion (nunca falla)
        return next == OrderItemStatus.CANCELLED ? releaser.release(result, actor) : result;
    }

    @Override
    public List<OrderItemHistoryEntry> history(Long orderId, Long itemId) {
        findItem(orderId, itemId);
        return historyRepository.findByOrderItemId(itemId);
    }

    /** Resumen corto de los campos editables: {@code sku/externalItemId/unitPrice}. */
    private static String describe(OrderItem item) {
        return item.getSku() + "/" + item.getExternalItemId() + "/" + item.getUnitPrice();
    }

    /** Con una solicitud de deshacer pendiente la unidad queda congelada (spec 10.2c-2); llamar con su lock tomado. */
    private void ensureNoPendingUndo(Long itemId) {
        undoRepository.findPendingByOrderItemId(itemId).ifPresent(pending -> {
            throw new UndoRequestPendingException("La unidad " + itemId
                    + " tiene una solicitud de deshacer asignacion pendiente: " + pending.getId());
        });
    }

    private OrderItem findItem(Long orderId, Long itemId) {
        return itemRepository.findByIdAndOrderId(itemId, orderId)
                .orElseThrow(() -> new OrderItemNotFoundException(itemId));
    }

    private OrderItem lockItem(Long orderId, Long itemId) {
        return itemRepository.findByIdAndOrderIdForUpdate(itemId, orderId)
                .orElseThrow(() -> new OrderItemNotFoundException(itemId));
    }

    private Order lockOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
