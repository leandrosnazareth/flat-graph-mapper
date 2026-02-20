package com.leandrosnazareth.flatgraph.metadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Aggregated metadata for a single DTO class, produced once by {@link MetadataExtractor}
 * and cached for the lifetime of the application.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code rootClass}             — the single root domain class identified from all {@code @ParentField} targets</li>
 *   <li>{@code parentMappings}        — ordered list of {@link FieldMapping} objects derived from {@code @ParentField}</li>
 *   <li>{@code childMappings}         — ordered list of {@link FieldMapping} objects derived from {@code @ChildField}</li>
 *   <li>{@code childMappingsByClass}  — child mappings pre-grouped by {@code targetClass}, in declaration order</li>
 * </ul>
 *
 * <p>{@code childMappingsByClass} is a {@link LinkedHashMap} so that
 * {@link #childClassesInOrder()} always returns child classes in the order they first
 * appeared in the DTO — which matches the natural JOIN hierarchy depth.
 */
public record ClassMetadata(
        Class<?> rootClass,
        List<FieldMapping> parentMappings,
        List<FieldMapping> childMappings,
        LinkedHashMap<Class<?>, List<FieldMapping>> childMappingsByClass
) {

    /**
     * Returns child classes in declaration order (depth-first: root children first,
     * leaf children last). Backed by the {@link LinkedHashMap} key set — O(1).
     */
    public Set<Class<?>> childClassesInOrder() {
        return childMappingsByClass.keySet();
    }

    /**
     * Returns all {@link FieldMapping}s for the given child {@code targetClass}.
     * Returns an empty list if the class is not registered (never null).
     */
    public List<FieldMapping> childMappingsFor(Class<?> targetClass) {
        return childMappingsByClass.getOrDefault(targetClass, List.of());
    }
}
