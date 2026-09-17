package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a CDI bean class that implements an <a href="https://modelcontextprotocol.io/extensions/overview">MCP
 * extension</a>.
 * <p>
 * An extension is advertised to clients during capability negotiation, as a key inside the {@code capabilities.extensions}
 * object of the {@code initialize} and {@code server/discover} responses. The key is the extension {@link #id()} and the value
 * is the extension's <em>settings object</em>, declared statically with {@link McpExtensionSetting} (an empty object when no
 * settings are declared).
 * <p>
 * The class may declare one or more {@link McpExtensionMethod} methods that handle custom top-level JSON-RPC methods (such as
 * {@code skills/list}).
 * <p>
 * The extension may be bound to specific server configurations with {@link McpServer}, following the same rules as feature
 * methods. If no binding is declared the extension is bound to the default server.
 *
 * @see McpExtensionSetting
 * @see McpExtensionMethod
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface McpExtension {

    /**
     * The extension identifier, in the form {@code {prefix}/{extension-name}}, e.g. {@code io.modelcontextprotocol/skills}.
     * <p>
     * The identifier must follow the {@code _meta} key naming rules and, unlike a generic {@code _meta} key, the prefix is
     * <strong>mandatory</strong>. The prefix must be a series of labels separated by dots ({@code .}) and followed by a slash
     * ({@code /}); labels must start with a letter and end with a letter or digit, with letters, digits, or hyphens
     * ({@code -}) in between. The name must begin and end with an alphanumeric character and may contain hyphens,
     * underscores ({@code _}), dots, and alphanumerics in between. Third-party extensions should use a reversed domain name
     * as the prefix; the {@code modelcontextprotocol} and {@code mcp} labels are reserved for the MCP spec.
     *
     * @return the extension identifier
     * @see MetaKey
     */
    String id();

}
