package com.example.msproducts.application.port.in;

import com.example.msproducts.domain.model.Category;

import java.util.List;
import java.util.Set;

public interface CategoryUseCase {

    Category create(String name, Long parentId);

    /** active null conserva el valor actual. */
    Category update(Long id, String name, Boolean active);

    List<CategoryNode> tree();

    /**
     * Resuelve una ruta de nombres (de raiz a hoja), comparando sin distinguir
     * mayusculas bajo la categoria anterior. Con createMissing crea los niveles
     * que falten; sin el, falla si alguno no existe.
     */
    Category resolvePath(List<String> names, boolean createMissing);

    /** Nombres de la raiz hasta la categoria indicada. */
    List<String> pathOf(Long categoryId);

    /** La categoria y todos sus descendientes. */
    Set<Long> selfAndDescendants(Long id);
}
