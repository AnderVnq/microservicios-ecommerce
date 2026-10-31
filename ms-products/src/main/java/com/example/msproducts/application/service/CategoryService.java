package com.example.msproducts.application.service;

import com.example.msproducts.application.port.in.CategoryNode;
import com.example.msproducts.application.port.in.CategoryUseCase;
import com.example.msproducts.application.port.out.CategoryRepositoryPort;
import com.example.msproducts.domain.exception.CategoryAlreadyExistsException;
import com.example.msproducts.domain.exception.CategoryDepthExceededException;
import com.example.msproducts.domain.exception.CategoryNotFoundException;
import com.example.msproducts.domain.model.Category;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Sin @Transactional a proposito. Los inserts y la relectura tras un choque del
 * unico corren en transacciones REQUIRES_NEW propias: asi funcionan igual aunque
 * el llamador (ej. la importacion masiva) ya tenga una transaccion abierta, a la
 * que el choque no puede dejar en rollback-only, y la relectura (READ_COMMITTED)
 * ve la fila que otro hilo acaba de confirmar.
 */
@Service
class CategoryService implements CategoryUseCase {

    private final CategoryRepositoryPort repository;
    private final TransactionTemplate insertTx;
    private final TransactionTemplate rereadTx;

    CategoryService(CategoryRepositoryPort repository, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.insertTx = new TransactionTemplate(txManager);
        this.insertTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rereadTx = new TransactionTemplate(txManager);
        this.rereadTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rereadTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public Category create(String name, Long parentId) {
        String clean = cleanName(name);
        int level = 1;
        if (parentId != null) {
            Category parent = repository.findById(parentId).orElseThrow(() -> new CategoryNotFoundException(parentId));
            level = parent.getLevel() + 1;
        }
        if (level > CategoryDepthExceededException.MAX_LEVEL) {
            throw new CategoryDepthExceededException();
        }
        return insertOrConflict(newCategory(clean, parentId, level), clean);
    }

    @Override
    public Category update(Long id, String name, Boolean active) {
        Category category = repository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
        String clean = cleanName(name);
        category.setName(clean);
        if (active != null) {
            category.setActive(active);
        }
        return insertOrConflict(category, clean);
    }

    @Override
    public List<CategoryNode> tree() {
        List<Category> all = repository.findAll();
        Map<Long, List<Category>> byParent = new LinkedHashMap<>();
        for (Category c : all) {
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        return nodes(byParent, null);
    }

    @Override
    public Category resolvePath(List<String> names, boolean createMissing) {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("La ruta de categoria no puede estar vacia");
        }
        if (names.size() > CategoryDepthExceededException.MAX_LEVEL) {
            throw new CategoryDepthExceededException();
        }
        Category current = null;
        for (String raw : names) {
            String clean = cleanName(raw);
            Long parentId = current == null ? null : current.getId();
            int level = current == null ? 1 : current.getLevel() + 1;
            current = repository.findByParentAndName(parentId, clean).orElse(null);
            if (current == null) {
                if (!createMissing) {
                    throw new CategoryNotFoundException(clean);
                }
                current = createOrReread(clean, parentId, level);
            }
        }
        return current;
    }

    @Override
    public List<String> pathOf(Long categoryId) {
        List<String> path = new ArrayList<>();
        Long id = categoryId;
        while (id != null) {
            Long currentId = id;
            Category c = repository.findById(currentId).orElseThrow(() -> new CategoryNotFoundException(currentId));
            path.add(c.getName());
            id = c.getParentId();
        }
        Collections.reverse(path);
        return path;
    }

    @Override
    public Set<Long> selfAndDescendants(Long id) {
        repository.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
        Map<Long, List<Long>> children = new LinkedHashMap<>();
        for (Category c : repository.findAll()) {
            if (c.getParentId() != null) {
                children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        Set<Long> result = new HashSet<>();
        List<Long> pending = new ArrayList<>(List.of(id));
        while (!pending.isEmpty()) {
            Long next = pending.remove(pending.size() - 1);
            if (result.add(next)) {
                pending.addAll(children.getOrDefault(next, List.of()));
            }
        }
        return result;
    }

    /** Si otro hilo creo el mismo hermano entre la lectura y el insert, devuelve el existente. */
    private Category createOrReread(String name, Long parentId, int level) {
        try {
            return saveInNewTx(newCategory(name, parentId, level));
        } catch (DataIntegrityViolationException e) {
            return rereadSibling(parentId, name).orElseThrow(() -> e);
        }
    }

    /** Guarda; si choca el unico (hermano con ese nombre) es duplicado, otro error de integridad se propaga. */
    private Category insertOrConflict(Category category, String name) {
        try {
            return saveInNewTx(category);
        } catch (DataIntegrityViolationException e) {
            Category sibling = rereadSibling(category.getParentId(), name).orElseThrow(() -> e);
            if (category.getId() != null && category.getId().equals(sibling.getId())) {
                throw e;
            }
            throw new CategoryAlreadyExistsException(name);
        }
    }

    private Category saveInNewTx(Category category) {
        return inTx(insertTx, () -> repository.save(category));
    }

    private Optional<Category> rereadSibling(Long parentId, String name) {
        return inTx(rereadTx, () -> repository.findByParentAndName(parentId, name));
    }

    private static <T> T inTx(TransactionTemplate template, Supplier<T> action) {
        return template.execute(status -> action.get());
    }

    private static List<CategoryNode> nodes(Map<Long, List<Category>> byParent, Long parentId) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> new CategoryNode(c.getId(), c.getName(), c.getLevel(), c.isActive(),
                        nodes(byParent, c.getId())))
                .toList();
    }

    private static Category newCategory(String name, Long parentId, int level) {
        return Category.builder().name(name).parentId(parentId).level(level).active(true).build();
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoria es obligatorio");
        }
        return name.trim();
    }
}
