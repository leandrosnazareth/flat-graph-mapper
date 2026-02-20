package com.leandrosnazareth.flatgraph.annotation;

import java.lang.annotation.*;

/**
 * Marks a DTO field as belonging to the ROOT (parent) domain class.
 *
 * <p>Usage example:
 * <pre>
 *   {@literal @}ParentField(target = Usuario.class, field = "nome")
 *   private String usuarioNome;
 * </pre>
 *
 * <p>Architectural decision: The root class is inferred from the first
 * {@code @ParentField} annotation found, so no explicit "root" marker is needed.
 *
 * @param target The root domain class that owns this field.
 * @param field  The exact field name in the target domain class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface ParentField {

    /** The root domain class that owns this field. */
    Class<?> target();

    /** The exact field name declared in {@link #target()}. */
    String field();
}
