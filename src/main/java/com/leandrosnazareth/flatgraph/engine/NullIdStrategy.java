package com.leandrosnazareth.flatgraph.engine;

/**
 * Defines how the {@link GraphBuildEngine} behaves when a child ID resolves to {@code null}.
 *
 * <p>In a LEFT JOIN result, rows where the child table has no match carry null for all child columns:
 * <pre>
 * idUsuario | nome  | roleId | roleNome
 * ----------+-------+--------+---------
 *     1     | Alice |  null  |  null      // usuario sem role
 *     2     | Bob   |  10    |  ADMIN
 * </pre>
 */
public enum NullIdStrategy {

    /**
     * Silently skip the child when its ID is {@code null}.
     * Parent is still created and populated. This is the <b>default</b>.
     */
    SKIP,

    /**
     * Throw {@link GraphMappingException} on the first {@code null} child ID.
     * Use for INNER JOIN results where nulls indicate a data-integrity problem.
     */
    THROW,

    /**
     * Create and populate the child even with a {@code null} ID.
     * Multiple null-ID rows for the same child class share one instance
     * (keyed by {@code (targetClass, null)}).
     */
    ALLOW_NULL_ID
}
