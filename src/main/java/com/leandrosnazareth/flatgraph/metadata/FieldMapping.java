package com.leandrosnazareth.flatgraph.metadata;

import java.lang.reflect.Field;

/**
 * Immutable value object that represents a single DTO field mapping.
 *
 * <p>Holds:
 * <ul>
 *   <li>{@code dtoField}    — the reflective handle of the DTO field (already accessible)</li>
 *   <li>{@code targetClass} — the domain class that owns the mapped field</li>
 *   <li>{@code targetField} — the reflective handle of the domain field (already accessible)</li>
 *   <li>{@code parentClass} — for child mappings, the domain class that holds the collection;
 *                             {@code null} for parent/root mappings</li>
 *   <li>{@code isId}        — true when the domain field is named "id" (used as dedup key)</li>
 * </ul>
 *
 * <p>Architectural decision: caching {@link Field} objects (and calling
 * {@link Field#setAccessible(boolean)}) once at startup eliminates repeated
 * security checks during the hot mapping loop.
 */
public record FieldMapping(
        Field dtoField,
        Class<?> targetClass,
        Field targetField,
        Class<?> parentClass,   // null for @ParentField mappings
        boolean isId
) {

    /**
     * Compact constructor — enforces accessibility eagerly so the
     * hot path never needs to call setAccessible again.
     */
    public FieldMapping {
        dtoField.setAccessible(true);
        targetField.setAccessible(true);
    }
}
