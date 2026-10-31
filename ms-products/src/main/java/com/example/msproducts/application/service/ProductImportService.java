package com.example.msproducts.application.service;

import com.example.msproducts.application.port.in.CategoryUseCase;
import com.example.msproducts.application.port.in.ImportResult;
import com.example.msproducts.application.port.in.ImportRow;
import com.example.msproducts.application.port.in.MarketSkuData;
import com.example.msproducts.application.port.in.ProductImportUseCase;
import com.example.msproducts.application.port.in.RowOutcome;
import com.example.msproducts.application.port.in.RowResult;
import com.example.msproducts.application.port.in.VariantData;
import com.example.msproducts.application.port.out.ActorProvider;
import com.example.msproducts.application.port.out.MarketsPort;
import com.example.msproducts.application.port.out.ProductRepositoryPort;
import com.example.msproducts.application.port.out.ResolvedMarketSku;
import com.example.msproducts.domain.exception.BusinessRuleException;
import com.example.msproducts.domain.model.Product;
import com.example.msproducts.domain.model.ProductStatus;
import com.example.msproducts.domain.model.ProductVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Carga masiva (spec 7.4). Todo lo que rechaza una fila se detecta ANTES de escribir:
 * validaciones sin base (ImportRowValidator) y consultas por bloque (SKUs y equivalencias
 * ya usadas). Asi ningun unico de la base falla dentro de la transaccion del bloque; si
 * igual falla algo (error imprevisto o una carrera con otra carga), se revierte el bloque
 * entero, sus filas quedan REJECTED con el error y se sigue con el siguiente.
 */
@Service
class ProductImportService implements ProductImportUseCase {

    /** Filas aproximadas por transaccion: los grupos (productos) nunca se parten. */
    static final int BLOCK_ROWS = 500;

    private static final Logger log = LoggerFactory.getLogger(ProductImportService.class);

    private final ProductRepositoryPort repository;
    private final CategoryUseCase categories;
    private final MarketsPort markets;
    private final ActorProvider actorProvider;
    private final VariantWriter writer;
    private final TransactionTemplate blockTx;

    ProductImportService(ProductRepositoryPort repository, CategoryUseCase categories, MarketsPort markets,
                         ActorProvider actorProvider, VariantWriter writer, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.categories = categories;
        this.markets = markets;
        this.actorProvider = actorProvider;
        this.writer = writer;
        this.blockTx = new TransactionTemplate(txManager);
    }

    @Override
    public ImportResult importRows(List<ImportRow> rows) {
        if (rows == null) {
            throw new IllegalArgumentException("La carga requiere filas");
        }
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("La carga admite como maximo %d filas (llegaron %d)"
                    .formatted(MAX_ROWS, rows.size()));
        }
        List<ImportLine> lines = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            lines.add(new ImportLine(i + 1, rows.get(i)));
        }
        // Antes de escribir nada: ms-markets caido -> 503 y la carga no toca la base
        Set<Long> unknownMarkets = unknownMarkets(rows);
        String actor = actorProvider.currentActor();
        lines.forEach(line -> ImportRowValidator.validate(line, unknownMarkets));
        rejectRepeated(lines);
        for (List<List<ImportLine>> block : blocks(groups(lines))) {
            runBlock(block, actor);
        }
        return result(lines);
    }

    // ---- antes de procesar ----

    private Set<Long> unknownMarkets(List<ImportRow> rows) {
        Set<Long> ids = rows.stream().filter(Objects::nonNull)
                .flatMap(r -> r.marketSkus().stream())
                .filter(m -> m != null && m.marketId() != null)
                .map(MarketSkuData::marketId)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<Long> unknown = new HashSet<>();
        for (Long id : ids) {
            if (!markets.exists(id)) {
                unknown.add(id);
            }
        }
        return unknown;
    }

    /** Mismo SKU (o misma equivalencia) en dos filas de la carga: gana la primera. */
    private static void rejectRepeated(List<ImportLine> lines) {
        Set<String> skus = new HashSet<>();
        for (ImportLine line : lines) {
            if (line.skuKey != null && !skus.add(line.skuKey)) {
                line.reject("SKU repetido en la carga");
            }
        }
        Map<String, ImportLine> claimed = new HashMap<>();
        for (ImportLine line : lines) {
            if (line.rejected()) {
                continue;
            }
            for (MarketSkuData m : line.marketSkus) {
                ImportLine owner = claimed.putIfAbsent(ImportLine.marketKey(m.marketId(), m.marketSku()), line);
                if (owner != null && owner != line) {
                    line.reject("El SKU de market %s (market %d) ya viene en la fila %d de la carga"
                            .formatted(m.marketSku(), m.marketId(), owner.index));
                    break;
                }
            }
        }
    }

    /** Grupos por mainSku en el orden en que aparecen; filas sin mainSku ya estan rechazadas. */
    private static Collection<List<ImportLine>> groups(List<ImportLine> lines) {
        Map<String, List<ImportLine>> groups = new LinkedHashMap<>();
        for (ImportLine line : lines) {
            if (line.mainKey != null) {
                groups.computeIfAbsent(line.mainKey, k -> new ArrayList<>()).add(line);
            }
        }
        return groups.values();
    }

    private static List<List<List<ImportLine>>> blocks(Collection<List<ImportLine>> groups) {
        List<List<List<ImportLine>>> blocks = new ArrayList<>();
        List<List<ImportLine>> current = new ArrayList<>();
        int size = 0;
        for (List<ImportLine> group : groups) {
            current.add(group);
            size += group.size();
            if (size >= BLOCK_ROWS) {
                blocks.add(current);
                current = new ArrayList<>();
                size = 0;
            }
        }
        if (!current.isEmpty()) {
            blocks.add(current);
        }
        return blocks;
    }

    // ---- bloque ----

    private void runBlock(List<List<ImportLine>> block, String actor) {
        try {
            blockTx.executeWithoutResult(status -> writeBlock(block, actor));
        } catch (RuntimeException e) {
            log.error("Carga masiva: se revierte un bloque de {} productos", block.size(), e);
            String reason = "Error inesperado al guardar el bloque, se revirtio: "
                    + NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            block.forEach(group -> group.forEach(line -> line.failBlock(reason)));
        }
    }

    private void writeBlock(List<List<ImportLine>> block, String actor) {
        BlockData data = load(block);
        for (List<ImportLine> group : block) {
            writeGroup(group, data, actor);
        }
    }

    /** Lo que el bloque necesita de la base, en pocas consultas (IN) en vez de una por fila. */
    private BlockData load(List<List<ImportLine>> block) {
        List<ImportLine> lines = block.stream().flatMap(List::stream).toList();
        Set<String> skus = new HashSet<>();
        lines.forEach(l -> {
            if (l.sku != null) {
                skus.add(l.sku);
            }
            skus.add(l.mainSku);
        });
        Map<String, ProductVariant> variants = repository.findVariantsBySkus(skus).stream()
                .collect(Collectors.toMap(v -> ImportLine.key(v.getSku()), Function.identity(), (a, b) -> a));
        Set<Long> productIds = block.stream().map(g -> variants.get(g.get(0).mainKey)).filter(Objects::nonNull)
                .map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products = repository.findProductsByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<MarketSkuData> claims = lines.stream().filter(l -> !l.rejected())
                .flatMap(l -> l.marketSkus.stream()).toList();
        Map<String, String> marketOwners = repository.findResolvedMarketSkus(
                        claims.stream().map(MarketSkuData::marketId).collect(Collectors.toSet()),
                        claims.stream().map(MarketSkuData::marketSku).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(r -> ImportLine.marketKey(r.marketId(), r.marketSku()),
                        ResolvedMarketSku::variantSku, (a, b) -> a));
        return new BlockData(variants, products, marketOwners, new HashMap<>());
    }

    private void writeGroup(List<ImportLine> group, BlockData data, String actor) {
        ImportLine first = group.get(0);
        ImportLine mainLine = group.stream().filter(ImportLine::isMainRow).findFirst().orElse(null);
        ProductVariant mainVariant = data.variants.get(first.mainKey);
        Long productId;
        if (mainVariant != null) {
            if (!mainVariant.isMain()) {
                rejectAll(group, "El mainSku %s es una variacion no principal de otro producto"
                        .formatted(first.mainSku));
                return;
            }
            productId = mainVariant.getProductId();
            if (mainLine != null && !mainLine.rejected()) {
                Product product = data.products.get(productId);
                mainLine.addDifferences(ImportRowValidator.parentDifferences(product,
                        currentPath(product, data), mainLine));
            }
        } else {
            if (mainLine == null) {
                rejectAll(group, "Grupo %s sin fila principal ni producto existente"
                        .formatted(first.mainSku));
                return;
            }
            String conflict = mainLine.rejected() ? null : conflict(mainLine, null, data);
            if (conflict != null) {
                mainLine.reject(conflict);
            }
            if (mainLine.rejected()) {
                rejectAll(group, "La fila principal %s fue rechazada".formatted(mainLine.sku));
                return;
            }
            productId = createProduct(mainLine, actor);
            if (productId == null) {
                rejectAll(group, "La fila principal %s fue rechazada".formatted(mainLine.sku));
                return;
            }
            writeLine(mainLine, productId, true, data, actor);
        }
        for (ImportLine line : group) {
            if (line.rejected() || (mainVariant == null && line == mainLine)) {
                continue;
            }
            writeLine(line, productId, false, data, actor);
        }
    }

    private Long createProduct(ImportLine main, String actor) {
        Long categoryId = null;
        if (!main.categoryPath.isEmpty()) {
            try {
                // Las categorias se crean en su propia transaccion: no ensucian la del bloque
                categoryId = categories.resolvePath(main.categoryPath, true).getId();
            } catch (IllegalArgumentException | BusinessRuleException e) {
                main.reject(e.getMessage());
                return null;
            }
        }
        ImportRow r = main.row;
        Instant now = Instant.now();
        return repository.saveProduct(Product.builder()
                .name(ImportLine.clean(r.name())).brand(ImportLine.clean(r.brand()))
                .description(ImportLine.clean(r.description())).categoryId(categoryId)
                .hsCode(ImportLine.clean(r.hsCode())).status(ProductStatus.ACTIVE)
                .createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now)
                .build()).getId();
    }

    private void writeLine(ImportLine line, Long productId, boolean main, BlockData data, String actor) {
        String conflict = conflict(line, productId, data);
        if (conflict != null) {
            line.reject(conflict);
            return;
        }
        ProductVariant existing = data.variants.get(line.skuKey);
        List<MarketSkuData> newMarketSkus = line.marketSkus.stream()
                .filter(m -> !data.marketOwners.containsKey(ImportLine.marketKey(m.marketId(), m.marketSku())))
                .toList();
        if (existing == null) {
            writer.insertVariant(productId, variantData(line, main, newMarketSkus), main, actor);
            line.done(RowOutcome.CREATED);
        } else {
            updateExisting(existing, line, newMarketSkus, actor);
        }
    }

    /** Motivo de rechazo por datos ya guardados, o null. productId null = producto nuevo. */
    private static String conflict(ImportLine line, Long productId, BlockData data) {
        ProductVariant existing = data.variants.get(line.skuKey);
        if (existing != null && !existing.getProductId().equals(productId)) {
            return "El SKU %s ya pertenece a otro producto".formatted(existing.getSku());
        }
        for (MarketSkuData m : line.marketSkus) {
            String owner = data.marketOwners.get(ImportLine.marketKey(m.marketId(), m.marketSku()));
            if (owner != null && !ImportLine.key(owner).equals(line.skuKey)) {
                return "El SKU de market %s (market %d) ya esta asignado a la variacion %s"
                        .formatted(m.marketSku(), m.marketId(), owner);
            }
        }
        return null;
    }

    /** SKU existente: solo precio y equivalencias nuevas; lo demas se informa y no se toca. */
    private void updateExisting(ProductVariant v, ImportLine line, List<MarketSkuData> newMarketSkus, String actor) {
        line.addDifferences(ImportRowValidator.variantDifferences(v, line));
        ImportRow r = line.row;
        boolean changed = false;
        if (r.referencePrice() != null && !ImportRowValidator.samePrice(v, r.referencePrice(), r.priceCurrency())) {
            Instant now = Instant.now();
            v.setReferencePrice(r.referencePrice());
            v.setPriceCurrency(r.priceCurrency());
            v.setPriceSourceUrl(ImportLine.clean(r.priceSourceUrl()));
            v.setPriceUpdatedAt(now);
            v.setUpdatedBy(actor);
            v.setUpdatedAt(now);
            repository.saveVariant(v);
            changed = true;
        }
        for (MarketSkuData m : newMarketSkus) {
            writer.insertMarketSku(v.getId(), m.marketId(), m.marketSku(), actor);
            changed = true;
        }
        line.done(changed ? RowOutcome.UPDATED : RowOutcome.UNCHANGED);
    }

    private static VariantData variantData(ImportLine line, boolean main, List<MarketSkuData> marketSkus) {
        ImportRow r = line.row;
        return new VariantData(line.sku, main, ImportLine.clean(r.color()), ImportLine.clean(r.size()),
                ImportLine.clean(r.barcode()), ImportLine.clean(r.asin()), line.weight, line.dimensions,
                r.referencePrice(), r.priceCurrency(), ImportLine.clean(r.priceSourceUrl()),
                ImportLine.clean(r.imageUrl()), null, marketSkus);
    }

    private List<String> currentPath(Product product, BlockData data) {
        Long categoryId = product.getCategoryId();
        return categoryId == null ? List.of() : data.paths.computeIfAbsent(categoryId, categories::pathOf);
    }

    private static void rejectAll(List<ImportLine> group, String reason) {
        group.forEach(line -> {
            if (!line.rejected()) {
                line.reject(reason);
            }
        });
    }

    private static ImportResult result(List<ImportLine> lines) {
        List<RowResult> rows = lines.stream().map(ImportLine::toResult).toList();
        Map<RowOutcome, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(RowResult::result, Collectors.counting()));
        return new ImportResult(rows.size(), count(counts, RowOutcome.CREATED), count(counts, RowOutcome.UPDATED),
                count(counts, RowOutcome.UNCHANGED), count(counts, RowOutcome.REJECTED), rows);
    }

    private static int count(Map<RowOutcome, Long> counts, RowOutcome outcome) {
        return counts.getOrDefault(outcome, 0L).intValue();
    }

    /** Variaciones por clave de SKU, productos por id, dueno (SKU) de cada equivalencia y rutas de categoria. */
    private record BlockData(Map<String, ProductVariant> variants, Map<Long, Product> products,
                             Map<String, String> marketOwners, Map<Long, List<String>> paths) {
    }
}
