package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.quarkiverse.mcp.server.McpExtensionSetting.McpExtensionSettings;

/**
 * Declares a single entry of an {@link McpExtension}'s <em>settings object</em>, i.e. the JSON object advertised as the value
 * of the extension key inside {@code capabilities.extensions}. It is a repeatable annotation.
 * <p>
 * The following extension advertises {@code "io.modelcontextprotocol/skills": { "directoryRead": true }}:
 *
 * <pre>
 * <code>
 * {@literal @McpExtension(id = "io.modelcontextprotocol/skills")}
 * {@literal @McpExtensionSetting(name = "directoryRead", type = Type.BOOLEAN, value = "true")}
 * class SkillsExtension {
 *     // ...
 * }
 * </code>
 * </pre>
 *
 * An extension that declares no {@code @McpExtensionSetting} is advertised with an empty settings object ({@code {}}).
 *
 * @see McpExtension
 */
@Retention(RUNTIME)
@Target(TYPE)
@Repeatable(McpExtensionSettings.class)
public @interface McpExtensionSetting {

    /**
     * The member name of this setting within the extension's settings object, e.g. {@code directoryRead}. The schema of the
     * settings object is defined by each extension, so this is an ordinary (non-empty) JSON object key with no MCP-imposed
     * naming rules.
     *
     * @return the name of the setting
     */
    String name();

    /**
     * @return the type of the value
     * @see #value()
     */
    MetaField.Type type() default MetaField.Type.STRING;

    /**
     * @return the value
     * @see #type()
     */
    String value();

    @Retention(RUNTIME)
    @Target(TYPE)
    @interface McpExtensionSettings {

        McpExtensionSetting[] value();

    }
}
