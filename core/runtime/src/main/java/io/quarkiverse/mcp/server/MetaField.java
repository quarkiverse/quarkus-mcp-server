package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.quarkiverse.mcp.server.MetaField.MetaFields;

/**
 * Represents an additional metadata field included in the {@code _meta} object of the relevant definition, such as {@code Tool}
 * or {@code Resource}. It is a repeatable annotation.
 * <p>
 * {@link Tool}, {@link Prompt}, {@link Resource} and {@link ResourceTemplate} methods can be annotated:
 *
 * <pre>
 * <code>
 * class Tools {
 *
 *     {@literal @MetaField(name = "priceLevel", value = "high")}
 *     {@literal @MetaField(name = "price", type = Type.INT, value = "100")}
 *     {@literal @Tool(description = "Converts the string value to lower case")}
 *     String toLowerCase(String value) {
 *        return value.toLowerCase();
 *     }
 *  }
 *  </code>
 * </pre>
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
@Repeatable(MetaFields.class)
public @interface MetaField {

    /**
     * Must be a series of labels separated by dots ({@code .}), followed by a slash ({@code /}).
     * Labels must start with a letter and end with a letter or digit; interior characters can be letters, digits, or hyphens
     * ({@code -}).
     * Some prefixes are reserved for the MCP spec, e.g. {@code modelcontextprotocol.io/} or {@code tools.mcp.com/}.
     *
     * @return the prefix (optional)
     * @see MetaKey
     */
    String prefix() default "";

    /**
     * Must begin and end with an alphanumeric character ({@code [a-z0-9A-Z]}).
     * May contain hyphens ({@code -}), underscores ({@code _}), dots ({@code .}), and alphanumerics in between.
     *
     * @return the name
     * @see MetaKey
     */
    String name();

    /**
     * @return the type of the value
     * @see #value()
     */
    Type type() default Type.STRING;

    /**
     * @return the value
     * @see #type()
     */
    String value();

    enum Type {
        /**
         * Represents a JSON string value.
         */
        STRING,
        /**
         * Represents a JSON number value.
         */
        INT,
        /**
         * Represents a JSON boolean value.
         */
        BOOLEAN,
        /**
         * Represents any valid JSON value, including arrays and objects.
         */
        JSON
    }

    @Retention(RUNTIME)
    @Target(METHOD)
    @interface MetaFields {

        MetaField[] value();

    }
}
