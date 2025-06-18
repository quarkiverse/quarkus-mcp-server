package io.quarkiverse.mcp.server.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.schema-generator.jakarta-validation")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpServerSchemaGeneratorJakartaValidationRuntimeConfig {

    /**
     * Whether to use the SchemaGenerator's Jakarta Validation Module.
     * If this module is not present as a dependency, this module won't be enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Corresponds to {@code JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED}.
     * <p>
     * If enabled, a field annotated with a "not-nullable" constraint (e.g., {@code @NotNull},
     * {@code @NotEmpty}, {@code @NotBlank}) will be marked as "required" in the generated schema.
     * </p>
     */
    @WithDefault("true")
    boolean notNullableFieldIsRequired();

    /**
     * Corresponds to {@code JakartaValidationOption.NOT_NULLABLE_METHOD_IS_REQUIRED}.
     * <p>
     * If enabled, a method (typically a getter) annotated with a "not-nullable" constraint
     * (e.g., {@code @NotNull}, {@code @NotEmpty}, {@code @NotBlank}) will be marked as
     * "required" in the generated schema.
     * </p>
     */
    @WithDefault("true")
    boolean notNullableMethodIsRequired();

    /**
     * Corresponds to {@code JakartaValidationOption.PREFER_IDN_EMAIL_FORMAT}.
     * <p>
     * If enabled, for properties annotated with {@code @Email}, the schema will use
     * the "idn-email" format instead of the standard "email" format.
     * </p>
     */
    @WithDefault("false")
    boolean preferIdnEmailFormat();

    /**
     * Corresponds to {@code JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS}.
     * <p>
     * If enabled, for properties annotated with {@code @Pattern}, the regular
     * expression will be included in the schema as a "pattern" attribute.
     * </p>
     */
    @WithDefault("true")
    boolean includePatternExpressions();
}
