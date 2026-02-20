package io.github.flatgraph.engine;

import io.github.flatgraph.metadata.ClassMetadata;
import io.github.flatgraph.metadata.FieldMapping;
import io.github.flatgraph.metadata.MetadataExtractor;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Core engine responsible for building an object graph from a flat list of DTOs.
 *
 * <h2>Algorithm overview (per DTO row)</h2>
 * <ol>
 *   <li>Extract the root ID. If {@code null}, skip the row entirely.</li>
 *   <li>Check if the root already exists in the identity map.
 *       <ul>
 *         <li>If <b>new</b>: instantiate and set all {@code @ParentField} values.</li>
 *         <li>If <b>existing</b>: reuse the cached instance — do NOT overwrite fields.</li>
 *       </ul>
 *   </li>
 *   <li>For each child class that appears in this row (in declaration order):
 *       <ul>
 *         <li>Extract the child ID. If {@code null}, skip this child entirely.</li>
 *         <li>If <b>new</b>: instantiate the child, set ALL its {@code @ChildField} values,
 *             then link it to its parent collection.</li>
 *         <li>If <b>existing</b>: reuse — do NOT overwrite fields, do NOT re-add to collection.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>Why "set fields only on first encounter"?</h2>
 * <p>In a JOIN result, every row repeats the parent (and intermediate child) data.
 * Writing the same values repeatedly is harmless but wasteful. More importantly,
 * doing so with a single-pass "always write" approach hides bugs where two rows
 * carry different values for the same ID — which would indicate a data inconsistency.
 * By writing only once we make such inconsistencies visible (the first value wins).
 *
 * <h2>Architectural decisions</h2>
 * <ul>
 *   <li>Identity maps are {@code LinkedHashMap} to preserve insertion order.</li>
 *   <li>Child maps are keyed by {@code ChildKey(targetClass, id)} to prevent
 *       collisions between different child types sharing the same numeric ID.</li>
 *   <li>Child fields are grouped by {@code targetClass} before processing so that
 *       all fields of a given child are set atomically in one pass.</li>
 *   <li>{@code collectionFieldCache} lives on the engine instance (one per DTO class)
 *       so it is computed at most once per {@code (parentClass, childClass)} pair
 *       across the entire application lifetime.</li>
 *   <li>All mutable graph state (rootMap, childMap) is local to {@link #build(List)},
 *       making the engine safe for concurrent invocations.</li>
 * </ul>
 *
 * @param <D> DTO type
 * @param <R> Root domain type
 */
public final class GraphBuildEngine<D, R> {

    private final Class<D> dtoClass;
    private final Class<R> rootClass;
    private final ClassMetadata metadata;
    private final NullIdStrategy nullIdStrategy;

    /**
     * Cache: "(parentClass#childClass)" → List field on parent.
     * Populated lazily, at most once per pair, and never mutated after that.
     * Safe for concurrent reads after the first write because Field references
     * are immutable once setAccessible is called.
     */
    private final Map<String, Field> collectionFieldCache = new HashMap<>();

    /** Constructs the engine with the default {@link NullIdStrategy#SKIP} strategy. */
    public GraphBuildEngine(Class<D> dtoClass) {
        this(dtoClass, NullIdStrategy.SKIP);
    }

    /**
     * Constructs the engine with an explicit null-ID strategy.
     *
     * @param dtoClass       the DTO class carrying {@code @ParentField}/{@code @ChildField}
     * @param nullIdStrategy how to handle rows where a child ID is {@code null}
     */
    @SuppressWarnings("unchecked")
    public GraphBuildEngine(Class<D> dtoClass, NullIdStrategy nullIdStrategy) {
        this.dtoClass        = dtoClass;
        this.nullIdStrategy  = nullIdStrategy;
        this.metadata        = MetadataExtractor.extract(dtoClass);
        this.rootClass       = (Class<R>) metadata.rootClass();
    }

    // -------------------------------------------------------------------------
    // public API
    // -------------------------------------------------------------------------

    /**
     * Builds the object graph from the given flat DTO list.
     *
     * @param rows flat DTO rows (e.g., result of a JOIN query); may be empty, never null
     * @return ordered list of root objects with fully populated child collections
     */
    public List<R> build(List<D> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        // root identity map  : rootId        → root instance   (insertion-ordered)
        final Map<Object, R>      rootMap  = new LinkedHashMap<>();
        // child identity map : ChildKey(targetClass, id) → child instance
        final Map<ChildKey, Object> childMap = new LinkedHashMap<>();

        for (D row : rows) {
            processRow(row, rootMap, childMap);
        }

        return new ArrayList<>(rootMap.values());
    }

    // -------------------------------------------------------------------------
    // row processing — single, linear pass with no redundant writes
    // -------------------------------------------------------------------------

    private void processRow(D row,
                             Map<Object, R> rootMap,
                             Map<ChildKey, Object> childMap) {

        // ── 1. Root ID ──────────────────────────────────────────────────────
        Object rootId = extractId(row, metadata.parentMappings());
        if (rootId == null) return;

        // ── 2. Root instance — create + populate only on first encounter ────
        boolean rootIsNew = !rootMap.containsKey(rootId);
        R root = rootMap.computeIfAbsent(rootId, id -> newInstance(rootClass));
        if (rootIsNew) {
            setFields(row, root, metadata.parentMappings());
        }

        // ── 3. Children — grouped by targetClass, in declaration order ──────
        //
        // We iterate childMappings grouped by targetClass so that all field
        // assignments for a given child object happen in one cohesive block,
        // avoiding the "create in one loop, set in another" anti-pattern.
        //
        // currentRowChildren tracks instances created/found FOR THIS ROW so
        // that intermediate parents (e.g. Role) can be resolved when linking
        // deeper children (e.g. Permissao → Role).
        Map<Class<?>, Object> currentRowChildren = new HashMap<>();

        for (Class<?> childClass : metadata.childClassesInOrder()) {

            // a) Extract child ID from this row
            Object childId = extractId(row, metadata.childMappingsFor(childClass));

            // b) Apply null-ID strategy
            if (childId == null) {
                switch (nullIdStrategy) {
                    case SKIP -> { continue; }
                    case THROW -> throw new GraphMappingException(
                        "Null ID encountered for child class '" + childClass.getName()
                        + "' while processing DTO '" + dtoClass.getName()
                        + "'. Use NullIdStrategy.SKIP to silently ignore, "
                        + "or NullIdStrategy.ALLOW_NULL_ID to allow null-keyed instances."
                    );
                    case ALLOW_NULL_ID -> { /* fall through — null is a valid key */ }
                }
            }

            ChildKey key = new ChildKey(childClass, childId);

            // b) Create + populate only on first encounter
            boolean childIsNew = !childMap.containsKey(key);
            Object child = childMap.computeIfAbsent(key, k -> newInstance(childClass));
            if (childIsNew) {
                setFields(row, child, metadata.childMappingsFor(childClass));
            }

            // c) Track for parent resolution of deeper children
            currentRowChildren.put(childClass, child);

            // d) Link to parent collection (only if the child was just created
            //    OR if it was just seen for the first time under this specific parent)
            Class<?> parentClass = metadata.childMappingsFor(childClass).get(0).parentClass();
            Object   parentInstance = resolveParentInstance(parentClass, root, currentRowChildren);
            if (parentInstance != null) {
                addToCollection(parentInstance, childClass, child);
            }
        }
    }

    // -------------------------------------------------------------------------
    // helpers: field extraction and assignment
    // -------------------------------------------------------------------------

    /**
     * Reads the "id" field value from {@code row} using the first mapping whose
     * {@link FieldMapping#isId()} is {@code true} within {@code mappings}.
     *
     * @return the ID value, or {@code null} if no id mapping exists or value is null
     */
    private Object extractId(D row, List<FieldMapping> mappings) {
        for (FieldMapping m : mappings) {
            if (m.isId()) {
                return readDtoField(row, m.dtoField());
            }
        }
        return null;
    }

    /**
     * Sets every domain field described by {@code mappings} on {@code target}
     * using the corresponding DTO field value from {@code row}.
     *
     * <p>Called exactly once per (instance, row-group) — no redundant writes.
     */
    private void setFields(D row, Object target, List<FieldMapping> mappings) {
        for (FieldMapping m : mappings) {
            setField(target, m.targetField(), readDtoField(row, m.dtoField()));
        }
    }

    // -------------------------------------------------------------------------
    // helper: resolve the parent instance for a child mapping
    // -------------------------------------------------------------------------

    private Object resolveParentInstance(Class<?> parentClass,
                                          R root,
                                          Map<Class<?>, Object> currentRowChildren) {
        if (parentClass.equals(rootClass)) {
            return root;
        }
        return currentRowChildren.get(parentClass);
    }

    // -------------------------------------------------------------------------
    // helper: add child to the List<?> collection field on parent
    // -------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addToCollection(Object parent, Class<?> childClass, Object child) {
        Field collectionField = resolveCollectionField(parent.getClass(), childClass);
        if (collectionField == null) return;

        try {
            List list = (List) collectionField.get(parent);
            if (list == null) {
                list = new ArrayList<>();
                collectionField.set(parent, list);
            }
            if (!list.contains(child)) {
                list.add(child);
            }
        } catch (IllegalAccessException e) {
            throw new GraphMappingException(
                "Cannot access collection field '" + collectionField.getName()
                + "' on " + parent.getClass().getName(), e);
        }
    }

    /**
     * Resolves and caches the {@code List<childClass>} field on {@code parentClass}
     * by scanning the entire class hierarchy and inspecting generic type parameters.
     */
    private Field resolveCollectionField(Class<?> parentClass, Class<?> childClass) {
        String cacheKey = parentClass.getName() + "#" + childClass.getName();
        return collectionFieldCache.computeIfAbsent(cacheKey, k -> {
            Class<?> cursor = parentClass;
            while (cursor != null && cursor != Object.class) {
                for (Field f : cursor.getDeclaredFields()) {
                    if (List.class.isAssignableFrom(f.getType())
                            && genericTypeMatches(f, childClass)) {
                        f.setAccessible(true);
                        return f;
                    }
                }
                cursor = cursor.getSuperclass();
            }
            return null; // parent has no List<childClass> — silently ignored
        });
    }

    /** Returns {@code true} if the sole generic type argument of {@code listField} equals {@code expected}. */
    private boolean genericTypeMatches(Field listField, Class<?> expected) {
        Type generic = listField.getGenericType();
        if (generic instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            return args.length == 1 && expected.equals(args[0]);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // low-level reflection helpers
    // -------------------------------------------------------------------------

    private Object readDtoField(D row, Field field) {
        try {
            return field.get(row);
        } catch (IllegalAccessException e) {
            throw new GraphMappingException(
                "Cannot read DTO field '" + field.getName() + "' on " + dtoClass.getName(), e);
        }
    }

    private void setField(Object instance, Field field, Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new GraphMappingException(
                "Cannot set field '" + field.getName() + "' on "
                + instance.getClass().getName(), e);
        }
    }

    private <T> T newInstance(Class<T> clazz) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new GraphMappingException(
                "Cannot instantiate '" + clazz.getName()
                + "'. Ensure it has a no-arg constructor (can be private).", e);
        }
    }

    // -------------------------------------------------------------------------
    // inner types
    // -------------------------------------------------------------------------

    /**
     * Composite key for the child identity map.
     * Record gives correct {@code equals}/{@code hashCode} automatically.
     */
    private record ChildKey(Class<?> targetClass, Object id) {}
}
