package com.example.msproducts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.msproducts.MySqlTestSupport;
import com.example.msproducts.application.port.in.CategoryNode;
import com.example.msproducts.application.port.in.CategoryUseCase;
import com.example.msproducts.domain.exception.CategoryAlreadyExistsException;
import com.example.msproducts.domain.exception.CategoryDepthExceededException;
import com.example.msproducts.domain.exception.NotFoundException;
import com.example.msproducts.domain.model.Category;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CategoryServiceTest extends MySqlTestSupport {

    @Autowired
    CategoryUseCase categories;

    @Test
    void create_levelsFromParent() {
        Category l1 = categories.create("  Electrónica ", null);
        Category l2 = categories.create("Celulares", l1.getId());
        Category l3 = categories.create("Android", l2.getId());

        assertThat(l1.getName()).isEqualTo("Electrónica");
        assertThat(l1.getLevel()).isEqualTo(1);
        assertThat(l2.getLevel()).isEqualTo(2);
        assertThat(l3.getLevel()).isEqualTo(3);
        assertThat(l3.getParentId()).isEqualTo(l2.getId());
        assertThat(l1.isActive()).isTrue();
        assertThat(l1.getCreatedAt()).isNotNull();
    }

    @Test
    void create_underLevel3_depthExceeded() {
        Category l3 = categories.resolvePath(List.of("A", "B", "C"), true);
        assertThatThrownBy(() -> categories.create("D", l3.getId()))
                .isInstanceOf(CategoryDepthExceededException.class);
    }

    @Test
    void create_unknownParent_notFound() {
        assertThatThrownBy(() -> categories.create("X", 999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void topLevelDuplicateName_rejectedByUniqueKey() {
        Category first = categories.create("Electrónica", null);

        assertThatThrownBy(() -> categories.create("Electrónica", null))
                .isInstanceOf(CategoryAlreadyExistsException.class);
        assertThatThrownBy(() -> categories.create("ELECTRONICA", null))
                .isInstanceOf(CategoryAlreadyExistsException.class);
        assertThat(categories.resolvePath(List.of("electrónica"), true).getId()).isEqualTo(first.getId());
    }

    @Test
    void resolvePath_createsMissingLevels() {
        Category leaf = categories.resolvePath(List.of("Hogar", "Cocina", "Ollas"), true);
        assertThat(leaf.getLevel()).isEqualTo(3);
        assertThat(categories.pathOf(leaf.getId())).containsExactly("Hogar", "Cocina", "Ollas");

        Category again = categories.resolvePath(List.of("hogar", "COCINA", "ollas"), true);
        assertThat(again.getId()).isEqualTo(leaf.getId());
    }

    @Test
    void resolvePath_doesNotCreateWhenFlagFalse() {
        assertThatThrownBy(() -> categories.resolvePath(List.of("Nada"), false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resolvePath_fourLevels_rejected() {
        assertThatThrownBy(() -> categories.resolvePath(List.of("A", "B", "C", "D"), true))
                .isInstanceOf(CategoryDepthExceededException.class);
    }

    @Test
    void tree_nested() {
        Category a = categories.create("A", null);
        categories.create("B", null);
        Category a1 = categories.create("A1", a.getId());
        categories.create("A1x", a1.getId());

        List<CategoryNode> tree = categories.tree();

        assertThat(tree).extracting(CategoryNode::name).containsExactly("A", "B");
        CategoryNode nodeA = tree.get(0);
        assertThat(nodeA.children()).extracting(CategoryNode::name).containsExactly("A1");
        assertThat(nodeA.children().get(0).children()).extracting(CategoryNode::name).containsExactly("A1x");
        assertThat(tree.get(1).children()).isEmpty();
    }

    @Test
    void selfAndDescendants() {
        Category a = categories.create("A", null);
        Category a1 = categories.create("A1", a.getId());
        Category a2 = categories.create("A2", a.getId());
        Category a1x = categories.create("A1x", a1.getId());
        Category b = categories.create("B", null);

        assertThat(categories.selfAndDescendants(a.getId()))
                .isEqualTo(Set.of(a.getId(), a1.getId(), a2.getId(), a1x.getId()));
        assertThat(categories.selfAndDescendants(b.getId())).isEqualTo(Set.of(b.getId()));
    }

    @Autowired
    PlatformTransactionManager txManager;

    @Test
    void resolvePath_insideOuterTx_concurrentDuplicate_returnsExistingAndOuterCommits() {
        TransactionTemplate outer = new TransactionTemplate(txManager);
        Long[] resolved = new Long[1];
        Long[] inserted = new Long[1];

        outer.executeWithoutResult(status -> {
            // Fija el snapshot de la transaccion externa: la categoria aun no existe
            assertThat(categories.tree()).isEmpty();
            // Otra transaccion (conexion propia) confirma la misma categoria
            jdbc.update("INSERT INTO categories (name, parent_id, level) VALUES ('Juguetes', NULL, 1)");
            inserted[0] = jdbc.queryForObject("SELECT id FROM categories WHERE name = 'Juguetes'", Long.class);

            resolved[0] = categories.resolvePath(List.of("juguetes"), true).getId();
        });

        assertThat(resolved[0]).isEqualTo(inserted[0]);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class)).isEqualTo(1);
    }

    @Test
    void update_nullActive_keepsCurrentValue() {
        Category a = categories.create("A", null);
        categories.update(a.getId(), "A", false);

        Category renamed = categories.update(a.getId(), "A2", null);

        assertThat(renamed.getName()).isEqualTo("A2");
        assertThat(renamed.isActive()).isFalse();
    }

    @Test
    void update_renamesAndDeactivates_duplicateRejected() {
        Category a = categories.create("A", null);
        categories.create("B", null);

        Category updated = categories.update(a.getId(), " A2 ", false);
        assertThat(updated.getName()).isEqualTo("A2");
        assertThat(updated.isActive()).isFalse();

        assertThatThrownBy(() -> categories.update(a.getId(), "b", true))
                .isInstanceOf(CategoryAlreadyExistsException.class);
        assertThatThrownBy(() -> categories.update(999L, "Z", true)).isInstanceOf(NotFoundException.class);
    }
}
