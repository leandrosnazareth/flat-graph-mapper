package io.github.flatgraph.engine;

/**
 * Unchecked exception thrown when the graph-building engine encounters a
 * reflection or configuration error.
 *
 * <p>Using an unchecked exception keeps the public API clean while still
 * propagating the root cause for diagnosis.
 */
public class GraphMappingException extends RuntimeException {

    public GraphMappingException(String message) {
        super(message);
    }

    public GraphMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
