package com.example.mspedidos.application.service;

import com.example.mspedidos.application.port.in.ProductLinkUseCase;
import com.example.mspedidos.application.port.out.ItemIncidentRepositoryPort;
import com.example.mspedidos.application.port.out.MarketSkuKey;
import com.example.mspedidos.application.port.out.OrderItemRepositoryPort;
import com.example.mspedidos.application.port.out.ProductsPort;
import com.example.mspedidos.domain.exception.ProductsUnavailableException;
import com.example.mspedidos.domain.model.HistoryEventType;
import com.example.mspedidos.domain.model.IncidentType;
import com.example.mspedidos.domain.model.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enlaza unidades pendientes (sin SKU interno) con el catalogo (spec 8.3). Primero resuelve TODAS las claves
 * pendientes contra ms-products, fuera de toda transaccion; si ms-products no responde no cambia nada y se reintenta
 * en la siguiente vuelta. Luego, por cada clave resuelta, una transaccion corta que solo bloquea esas unidades (una
 * sola lectura con lock, por id ascendente: subconjunto del orden global orden -> cupo -> unidades). La incidencia
 * {@code PRODUCT_LINK_PENDING} la gestiona el sistema: se cierra aqui directamente, no por la API de incidencias.
 */
@Slf4j
@Service
public class ProductLinker implements ProductLinkUseCase {

    static final String ACTOR = "system";

    private final OrderItemRepositoryPort itemRepository;
    private final ItemIncidentRepositoryPort incidentRepository;
    private final ProductsPort productsPort;
    private final ItemHistoryRecorder history;
    private final TransactionTemplate tx;

    ProductLinker(OrderItemRepositoryPort itemRepository, ItemIncidentRepositoryPort incidentRepository,
                  ProductsPort productsPort, ItemHistoryRecorder history,
                  PlatformTransactionManager transactionManager) {
        this.itemRepository = itemRepository;
        this.incidentRepository = incidentRepository;
        this.productsPort = productsPort;
        this.history = history;
        this.tx = new TransactionTemplate(transactionManager);
        // READ COMMITTED: la lectura con lock debe ver lo que confirmo un enlace manual concurrente
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public int linkPending() {
        Map<Long, List<String>> byMarket = new LinkedHashMap<>();
        for (MarketSkuKey key : itemRepository.findPendingMarketSkus()) {
            byMarket.computeIfAbsent(key.marketId(), m -> new ArrayList<>()).add(key.marketSku());
        }
        if (byMarket.isEmpty()) {
            return 0;
        }
        // llamadas remotas, sin locks (ProductsPort ya parte en lotes)
        Map<MarketSkuKey, String> resolved = new LinkedHashMap<>();
        try {
            byMarket.forEach((marketId, marketSkus) -> productsPort.resolve(marketId, marketSkus)
                    .forEach((marketSku, sku) -> resolved.put(new MarketSkuKey(marketId, marketSku), sku)));
        } catch (ProductsUnavailableException e) {
            log.warn("ms-products no disponible; las unidades pendientes se enlazaran en la siguiente vuelta: {}",
                    e.getMessage());
            return 0;
        } catch (RuntimeException e) {
            // p. ej. ms-products rechazo la peticion (IllegalStateException): no se aborta el job ni el endpoint
            log.warn("Error al resolver SKUs pendientes en ms-products; se reintentara en la siguiente vuelta", e);
            return 0;
        }
        int linked = 0;
        for (Map.Entry<MarketSkuKey, String> entry : resolved.entrySet()) {
            try {
                Integer count = tx.execute(status -> link(entry.getKey(), entry.getValue()));
                linked += count == null ? 0 : count;
            } catch (RuntimeException e) {
                // una clave que falla no frena a las demas; se reintenta en la siguiente vuelta
                log.warn("No se pudieron enlazar las unidades de {} al SKU {}: {}", entry.getKey(), entry.getValue(),
                        e.getMessage());
            }
        }
        if (linked > 0) {
            log.info("Enlazadas {} unidades pendientes con el catalogo", linked);
        }
        return linked;
    }

    private int link(MarketSkuKey key, String sku) {
        List<Long> ids = itemRepository.findPendingIds(key.marketId(), key.marketSku());
        int linked = 0;
        for (OrderItem item : itemRepository.findByIdsForUpdate(ids)) {
            // re-chequeo con lock: pudo enlazarse a mano, cancelarse o cambiar de SKU de market entretanto
            // (sin distinguir mayusculas, igual que la comparacion de MySQL con la que se eligio)
            if (!item.isProductLinkPending() || item.getStatus().isTerminal()
                    || !key.marketSku().equalsIgnoreCase(item.getMarketSku())) {
                continue;
            }
            item.linkSku(sku);
            itemRepository.save(item);
            Long itemId = item.getId();
            history.record(itemId, HistoryEventType.SKU_LINKED, null, sku, ACTOR);
            incidentRepository.findOpen(itemId, IncidentType.PRODUCT_LINK_PENDING).ifPresent(incident -> {
                incident.resolve(ACTOR);
                incidentRepository.save(incident);
                history.record(itemId, HistoryEventType.INCIDENT_RESOLVED, IncidentType.PRODUCT_LINK_PENDING.name(),
                        null, ACTOR);
            });
            linked++;
        }
        return linked;
    }
}
