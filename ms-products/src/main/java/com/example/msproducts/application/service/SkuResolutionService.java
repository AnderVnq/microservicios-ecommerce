package com.example.msproducts.application.service;

import com.example.msproducts.application.port.in.MarketSkuRef;
import com.example.msproducts.application.port.in.SkuResolution;
import com.example.msproducts.application.port.in.SkuResolutionUseCase;
import com.example.msproducts.application.port.out.ProductRepositoryPort;
import com.example.msproducts.application.port.out.ResolvedMarketSku;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * La busqueda real la hace la base (collation sin distinguir mayusculas). Al
 * asociar los resultados con las entradas en Java se compara trim + toLowerCase(ROOT),
 * que replica la collation para SKUs ASCII. Dos consultas como maximo por llamada.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkuResolutionService implements SkuResolutionUseCase {

    static final int MAX_ITEMS = 500;

    private final ProductRepositoryPort repository;

    @Override
    public List<SkuResolution> resolve(List<MarketSkuRef> refs) {
        checkSize(refs == null ? 0 : refs.size());
        if (refs.isEmpty()) {
            return List.of();
        }
        Set<Long> marketIds = new HashSet<>();
        Set<String> trimmed = new HashSet<>();
        for (MarketSkuRef r : refs) {
            if (r == null || r.marketId() == null || r.marketSku() == null) {
                throw new IllegalArgumentException("Cada entrada requiere marketId y marketSku");
            }
            marketIds.add(r.marketId());
            trimmed.add(r.marketSku().trim());
        }

        // Paso 1: equivalencias del market (tienen prioridad)
        Map<String, String> byEquivalence = new HashMap<>();
        for (ResolvedMarketSku e : repository.findResolvedMarketSkus(marketIds, trimmed)) {
            byEquivalence.put(pairKey(e.marketId(), e.marketSku()), e.variantSku());
        }

        // Paso 2: SKU interno para los que no tuvieron equivalencia
        Set<String> unresolved = new HashSet<>();
        for (MarketSkuRef r : refs) {
            if (!byEquivalence.containsKey(pairKey(r.marketId(), r.marketSku()))) {
                unresolved.add(r.marketSku().trim());
            }
        }
        Map<String, String> byInternal = new HashMap<>();
        for (String stored : repository.findExistingVariantSkus(unresolved)) {
            byInternal.put(norm(stored), stored);
        }

        return refs.stream().map(r -> {
            String sku = byEquivalence.get(pairKey(r.marketId(), r.marketSku()));
            if (sku == null) {
                sku = byInternal.get(norm(r.marketSku()));
            }
            return new SkuResolution(r.marketId(), r.marketSku(), sku);
        }).toList();
    }

    @Override
    public Map<String, String> canonical(List<String> skus) {
        checkSize(skus == null ? 0 : skus.size());
        if (skus.isEmpty()) {
            return Map.of();
        }
        Set<String> trimmed = new HashSet<>();
        for (String s : skus) {
            if (s == null) {
                throw new IllegalArgumentException("Los SKUs no pueden ser nulos");
            }
            trimmed.add(s.trim());
        }
        Map<String, String> byNorm = new HashMap<>();
        for (String stored : repository.findExistingVariantSkus(trimmed)) {
            byNorm.put(norm(stored), stored);
        }
        // Clave: la entrada tal como llego; valor: la escritura guardada en el catalogo
        Map<String, String> result = new LinkedHashMap<>();
        for (String s : skus) {
            String stored = byNorm.get(norm(s));
            if (stored != null) {
                result.put(s, stored);
            }
        }
        return result;
    }

    private static void checkSize(int size) {
        if (size > MAX_ITEMS) {
            throw new IllegalArgumentException("Maximo " + MAX_ITEMS + " elementos por llamada");
        }
    }

    private static String norm(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static String pairKey(Long marketId, String sku) {
        return marketId + "|" + norm(sku);
    }
}
