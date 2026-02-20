package com.leandrosnazareth.flatgraph.annotation;

import java.lang.annotation.*;

/**
 * Marks a DTO field as belonging to a CHILD domain class that will be collected
 * inside its declared parent.
 *
 * <p>Usage example:
 * <pre>
 *   {@literal @}ChildField(target = Role.class, field = "id", parent = Usuario.class)
 *   private Long roleId;
 * </pre>
 *
 * <p>Architectural decision: {@code parent} establishes the edge in the object graph,
 * allowing the engine to navigate arbitrarily deep hierarchies (e.g. Role → Permissao).
 *
 * @param target The child domain class that owns this field.
 * @param field  The exact field name declared in {@link #target()}.
 * @param parent The direct parent domain class that will hold a collection of {@link #target()}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface ChildField {

    /** The child domain class that owns this field. */
    Class<?> target();

    /** The exact field name declared in {@link #target()}. */
    String field();

    /**
     * The direct parent domain class that will hold a {@code List} of {@link #target()}.
     * The parent class must contain a {@code List<target>} field.
     */
    Class<?> parent();
}
