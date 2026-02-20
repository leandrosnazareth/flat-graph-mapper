package com.leandrosnazareth.flatgraph;

import com.leandrosnazareth.flatgraph.engine.GraphBuildEngine;
import com.leandrosnazareth.flatgraph.engine.NullIdStrategy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>FlatGraphMapper — Public API</h1>
 *
 * <p>Entry point for the FlatGraphMapper library. Transforms a flat list of
 * annotated DTOs (typically the result of a SQL JOIN query) into a hierarchical
 * object graph.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * List<UsuarioDTO> rows = jdbcTemplate.query(SQL, new UsuarioDTORowMapper());
 * List<Usuario>    users = FlatGraphMapper.map(rows, UsuarioDTO.class);
 * }</pre>
 *
 * <h2>Thread-safety</h2>
 * <p>{@link GraphBuildEngine} instances are cached per DTO class in a
 * {@link ConcurrentHashMap}.  Each call to {@link #map(List, Class)} uses a
 * <em>fresh local state</em> (the identity maps are local variables), so the
 * same cached engine can be safely used by multiple threads concurrently.
 *
 * <h2>Performance notes</h2>
 * <ul>
 *   <li>Annotation scanning and {@code Field#setAccessible} calls are done exactly
 *       once per DTO class, at first use.</li>
 *   <li>For large result sets the dominant cost is object instantiation
 *       ({@code newInstance}) and map lookups — both O(1) per row.</li>
 * </ul>
 */
public final class FlatGraphMapper {

    /**
     * Engine cache key: combines DTO class + NullIdStrategy so that engines with
     * different strategies are stored and reused independently.
     */
    private record EngineKey(Class<?> dtoClass, NullIdStrategy strategy) {}

    /** Thread-safe engine cache: one pre-built engine per (dtoClass, strategy) pair. */
    private static final Map<EngineKey, GraphBuildEngine<?, ?>> ENGINE_CACHE
            = new ConcurrentHashMap<>();

    private FlatGraphMapper() {}

    /**
     * Transforms {@code rows} into a hierarchical object graph using the default
     * {@link NullIdStrategy#SKIP} strategy — rows with a {@code null} child ID are silently ignored.
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
     * Transforms {@code rows} into a hierarchical object graph using the specified
     * {@link NullIdStrategy} to control behaviour when a child ID is {@code null}.
     *
     * <h3>Strategy options</h3>
     * <ul>
     *   <li>{@link NullIdStrategy#SKIP}          — silently ignore the child row (default)</li>
     *   <li>{@link NullIdStrategy#THROW}         — fail fast with {@link io.github.flatgraph.engine.GraphMappingException}</li>
     *   <li>{@link NullIdStrategy#ALLOW_NULL_ID} — create the child with a {@code null} ID</li>
     * </ul>
     *
     * @param rows        flat DTO list (may be empty, never null)
     * @param dtoClass    the DTO class carrying {@code @ParentField} / {@code @ChildField} annotations
     * @param nullIdStrategy how to handle rows where a child ID is {@code null}
     * @param <D>         DTO type
     * @param <R>         root domain type (inferred automatically)
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
}
