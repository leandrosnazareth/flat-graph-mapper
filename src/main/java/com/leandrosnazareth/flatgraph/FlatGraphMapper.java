package com.leandrosnazareth.flatgraph;

import com.leandrosnazareth.flatgraph.engine.GraphBuildEngine;
import com.leandrosnazareth.flatgraph.engine.NullIdStrategy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>FlatGraphMapper — Public API</h1>
 *
 * <p>Entry point for the FlatGraphMapper library. Transforms flat DTOs
 * (typically the result of a SQL JOIN query) into hierarchical object graphs.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // List
 * List<Usuario> users = FlatGraphMapper.map(rows, UsuarioDTO.class);
 *
 * // Single object
 * Optional<Usuario> user = FlatGraphMapper.mapSingle(dto, UsuarioDTO.class);
 * }</pre>
 *
 * <h2>Thread-safety</h2>
 * <p>{@link GraphBuildEngine} instances are cached per DTO class in a
 * {@link ConcurrentHashMap}. Each call uses fresh local state (identity maps),
 * so the same cached engine is safe for concurrent use.
 *
 * <h2>Performance notes</h2>
 * <ul>
 *   <li>Annotation scanning and {@code Field#setAccessible} calls are done
 *       exactly once per DTO class, at first use.</li>
 *   <li>For large result sets the dominant cost is object instantiation
 *       and map lookups — both O(1) per row.</li>
 * </ul>
 */
public final class FlatGraphMapper {

    private record EngineKey(Class<?> dtoClass, NullIdStrategy strategy) {}

    private static final Map<EngineKey, GraphBuildEngine<?, ?>> ENGINE_CACHE = new ConcurrentHashMap<>();

    private FlatGraphMapper() {}

    /**
     * Transforms a flat DTO list into a hierarchical object graph using the default
     * {@link NullIdStrategy#SKIP} strategy.
     *
     * @param rows     flat DTO list (may be empty, never null)
     * @param dtoClass the DTO class carrying {@code @ParentField} / {@code @ChildField} annotations
     * @param <D>      DTO type
     * @param <R>      root domain type (inferred automatically)
     * @return ordered list of root objects with fully populated child collections
     */
    public static <D, R> List<R> map(List<D> rows, Class<D> dtoClass) {
        return map(rows, dtoClass, NullIdStrategy.SKIP);
    }

    /**
     * Transforms a flat DTO list into a hierarchical object graph using the specified
     * {@link NullIdStrategy}.
     *
     * <h3>Strategy options</h3>
     * <ul>
     *   <li>{@link NullIdStrategy#SKIP}          — silently ignore child rows with null ID (default)</li>
     *   <li>{@link NullIdStrategy#THROW}         — fail fast with {@link com.leandrosnazareth.flatgraph.engine.GraphMappingException}</li>
     *   <li>{@link NullIdStrategy#ALLOW_NULL_ID} — create children with null ID</li>
     * </ul>
     *
     * @param rows           flat DTO list (may be empty, never null)
     * @param dtoClass       the DTO class carrying {@code @ParentField} / {@code @ChildField} annotations
     * @param nullIdStrategy how to handle rows where a child ID is {@code null}
     * @param <D>            DTO type
     * @param <R>            root domain type (inferred automatically)
     * @return ordered list of root objects with fully populated child collections
     */
    @SuppressWarnings("unchecked")
    public static <D, R> List<R> map(List<D> rows, Class<D> dtoClass, NullIdStrategy nullIdStrategy) {
        EngineKey key = new EngineKey(dtoClass, nullIdStrategy);
        GraphBuildEngine<D, R> engine =
            (GraphBuildEngine<D, R>) ENGINE_CACHE.computeIfAbsent(
                key, k -> new GraphBuildEngine<>(k.dtoClass(), k.strategy()));
        return engine.build(rows);
    }

    /**
     * Transforms a single DTO object into a root domain object using the default
     * {@link NullIdStrategy#SKIP} strategy.
     *
     * <p>Returns an {@link Optional} containing the mapped object, or
     * {@link Optional#empty()} if the input is {@code null}.
     *
     * @param row      a single flat DTO object (may be null)
     * @param dtoClass the DTO class carrying {@code @ParentField} / {@code @ChildField} annotations
     * @param <D>      DTO type
     * @param <R>      root domain type (inferred automatically)
     * @return Optional containing the mapped root object, or empty if input is null
     */
    public static <D, R> Optional<R> mapSingle(D row, Class<D> dtoClass) {
        return mapSingle(row, dtoClass, NullIdStrategy.SKIP);
    }

    /**
     * Transforms a single DTO object into a root domain object using the specified
     * {@link NullIdStrategy}.
     *
     * <p>Returns an {@link Optional} containing the mapped object, or
     * {@link Optional#empty()} if the input is {@code null}.
     *
     * @param row            a single flat DTO object (may be null)
     * @param dtoClass       the DTO class carrying {@code @ParentField} / {@code @ChildField} annotations
     * @param nullIdStrategy how to handle rows where a child ID is {@code null}
     * @param <D>            DTO type
     * @param <R>            root domain type (inferred automatically)
     * @return Optional containing the mapped root object, or empty if input is null
     */
    public static <D, R> Optional<R> mapSingle(D row, Class<D> dtoClass, NullIdStrategy nullIdStrategy) {
        if (row == null) return Optional.empty();
        List<R> results = map(List.of(row), dtoClass, nullIdStrategy);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Transforms a single DTO object into a root domain object using the default
     * {@link NullIdStrategy#SKIP} strategy.
     *
     * <p>Returns the mapped object, or {@code null} if the input is {@code null}.
     * This method is useful when you want to avoid Optional syntax.
     *
     * @param row            a single flat DTO object (may be null)
     * @param dtoClass       the DTO class carrying {@code @ParentField} / {@code @ChildField} annotations
     * @param resultType     the expected root domain type (required for type inference)
     * @param <D>            DTO type
     * @param <R>            root domain type
     * @return the mapped root object, or null if input is null
     */
    public static <D, R> R mapSingleOrNull(D row, Class<D> dtoClass, Class<R> resultType) {
        return mapSingleOrNull(row, dtoClass, resultType, NullIdStrategy.SKIP);
    }

    /**
     * Transforms a single DTO object into a root domain object using the specified
     * {@link NullIdStrategy}.
     *
     * <p>Returns the mapped object, or {@code null} if the input is {@code null}.
     * This method is useful when you want to avoid Optional syntax.
     *
     * @param row            a single flat DTO object (may be null)
     * @param dtoClass       the DTO class carrying {@code @ParentField} / {@code @ChildField} annotations
     * @param resultType     the expected root domain type (required for type inference)
     * @param nullIdStrategy how to handle rows where a child ID is {@code null}
     * @param <D>            DTO type
     * @param <R>            root domain type
     * @return the mapped root object, or null if input is null
     */
    public static <D, R> R mapSingleOrNull(D row, Class<D> dtoClass, Class<R> resultType, NullIdStrategy nullIdStrategy) {
        Optional<R> result = mapSingle(row, dtoClass, nullIdStrategy);
        return result.orElse(null);
    }
}
