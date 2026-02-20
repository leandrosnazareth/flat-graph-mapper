package com.leandrosnazareth.flatgraph.metadata;

import com.leandrosnazareth.flatgraph.annotation.ChildField;
import com.leandrosnazareth.flatgraph.annotation.ParentField;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responsible for extracting and CACHING metadata from DTO classes.
 *
 * <h2>Architectural decisions</h2>
 * <ul>
 *   <li>A {@link ConcurrentHashMap} is used as the cache so that multiple threads
 *       can call {@link #extract(Class)} concurrently without locking.
 *       {@code computeIfAbsent} guarantees at-most-one computation per key.</li>
 *   <li>All reflection work ({@link Field#setAccessible}) is done here, once,
 *       rather than on every row of the DTO list.</li>
 *   <li>The extractor is a singleton utility class; it does not hold mutable state
 *       beyond the cache itself.</li>
 * </ul>
 */
public final class MetadataExtractor {

    /** Thread-safe cache: DTO class → pre-computed metadata. */
    private static final Map<Class<?>, ClassMetadata> CACHE = new ConcurrentHashMap<>();

    private MetadataExtractor() {
        // utility class
    }

    /**
     * Returns the {@link ClassMetadata} for {@code dtoClass}, computing and caching it
     * on the first call and returning the cached value on subsequent calls.
     *
     * @param dtoClass the DTO class annotated with {@code @ParentField} / {@code @ChildField}
     * @return fully populated {@link ClassMetadata}
     * @throws IllegalArgumentException if {@code dtoClass} has no {@code @ParentField} annotations
     */
    public static ClassMetadata extract(Class<?> dtoClass) {
        return CACHE.computeIfAbsent(dtoClass, MetadataExtractor::buildMetadata);
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private static ClassMetadata buildMetadata(Class<?> dtoClass) {
        List<FieldMapping> parentMappings = new ArrayList<>();
        List<FieldMapping> childMappings  = new ArrayList<>();

        // LinkedHashMap preserves the declaration order of child classes in the DTO,
        // which naturally matches hierarchy depth (parent → child → grandchild).
        LinkedHashMap<Class<?>, List<FieldMapping>> childMappingsByClass = new LinkedHashMap<>();

        Class<?> rootClass = null;

        for (Field dtoField : dtoClass.getDeclaredFields()) {

            ParentField pf = dtoField.getAnnotation(ParentField.class);
            if (pf != null) {
                Field domainField = resolveField(pf.target(), pf.field(), dtoClass, dtoField.getName());
                boolean isId = "id".equals(pf.field());
                parentMappings.add(new FieldMapping(dtoField, pf.target(), domainField, null, isId));

                if (rootClass == null) {
                    rootClass = pf.target();
                } else if (!rootClass.equals(pf.target())) {
                    throw new IllegalArgumentException(
                        "Multiple root classes found in @ParentField annotations on " + dtoClass.getName()
                        + ": " + rootClass.getName() + " vs " + pf.target().getName()
                        + ". All @ParentField annotations must point to the same root class."
                    );
                }
                continue;
            }

            ChildField cf = dtoField.getAnnotation(ChildField.class);
            if (cf != null) {
                Field domainField = resolveField(cf.target(), cf.field(), dtoClass, dtoField.getName());
                boolean isId = "id".equals(cf.field());
                FieldMapping mapping = new FieldMapping(dtoField, cf.target(), domainField, cf.parent(), isId);
                childMappings.add(mapping);

                // Group by targetClass — computeIfAbsent preserves insertion order in LinkedHashMap
                childMappingsByClass.computeIfAbsent(cf.target(), k -> new ArrayList<>()).add(mapping);
            }
        }

        if (rootClass == null) {
            throw new IllegalArgumentException(
                "No @ParentField annotations found on " + dtoClass.getName()
                + ". At least one @ParentField is required to identify the root class."
            );
        }

        // Make inner lists unmodifiable
        childMappingsByClass.replaceAll((k, v) -> List.copyOf(v));

        return new ClassMetadata(
            rootClass,
            List.copyOf(parentMappings),
            List.copyOf(childMappings),
            childMappingsByClass
        );
    }

    /**
     * Resolves and returns the {@link Field} named {@code fieldName} on {@code domainClass},
     * searching the entire class hierarchy.
     */
    private static Field resolveField(Class<?> domainClass, String fieldName,
                                       Class<?> dtoClass, String dtoFieldName) {
        Class<?> cursor = domainClass;
        while (cursor != null && cursor != Object.class) {
            try {
                return cursor.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new IllegalArgumentException(
            "Field '" + fieldName + "' not found on domain class '" + domainClass.getName()
            + "'. Declared on DTO field '" + dtoClass.getName() + "#" + dtoFieldName + "'."
        );
    }
}
