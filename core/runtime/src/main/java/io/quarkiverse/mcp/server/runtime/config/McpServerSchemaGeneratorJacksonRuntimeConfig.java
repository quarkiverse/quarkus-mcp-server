package io.quarkiverse.mcp.server.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.mcp.server.schema-generator.jackson")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface McpServerSchemaGeneratorJacksonRuntimeConfig {

    /**
     * Whether to use the SchemaGenerator's Jackson Module.
     * If this module is not present as a dependency, this module won't be enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Corresponds to {@code JacksonOption.RESPECT_JSONPROPERTY_ORDER}.
     * <p>
     * If enabled, the order of properties in the generated schema will respect
     * the order defined in a {@code @JsonPropertyOrder} annotation on a given type.
     * </p>
     */
    @WithDefault("true")
    boolean respectJsonPropertyOrder();

    /**
     * Corresponds to {@code JacksonOption.RESPECT_JSONPROPERTY_REQUIRED}.
     * <p>
     * If enabled, a property will be marked as "required" in the schema if its
     * corresponding field or method is annotated with {@code @JsonProperty(required = true)}.
     * </p>
     */
    @WithDefault("true")
    boolean respectJsonPropertyRequired();

    /**
     * Corresponds to {@code JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE}.
     * <p>
     * If enabled, the schema for an enum will be a simple array of values (e.g., strings)
     * derived from the method annotated with {@code @JsonValue}.
     * </p>
     */
    @WithDefault("true")
    boolean flattenedEnumsFromJsonValue();

    /**
     * Corresponds to {@code JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY}.
     * <p>
     * If enabled, the schema for an enum will be derived from {@code @JsonProperty}
     * annotations on the enum's constants.
     * </p>
     */
    @WithDefault("false")
    boolean flattenedEnumsFromJsonProperty();

    /**
     * Corresponds to {@code JacksonOption.INCLUDE_ONLY_JSONPROPERTY_ANNOTATED_METHODS}.
     * <p>
     * If enabled, only methods explicitly annotated with {@code @JsonProperty} will be
     * included in the schema.
     * </p>
     */
    @WithDefault("false")
    boolean includeOnlyJsonPropertyAnnotatedMethods();

    /**
     * Corresponds to {@code JacksonOption.IGNORE_PROPERTY_NAMING_STRATEGY}.
     * <p>
     * If enabled, any configured {@code PropertyNamingStrategy} (e.g., snake_case)
     * will be ignored, and field names from the Java class will be used directly.
     * </p>
     */
    @WithDefault("false")
    boolean ignorePropertyNamingStrategy();

    /**
     * Corresponds to {@code JacksonOption.ALWAYS_REF_SUBTYPES}.
     * <p>
     * If enabled, subtypes in a polymorphic hierarchy will always be represented
     * by a {@code $ref} to a definition, rather than being inlined.
     * </p>
     */
    @WithDefault("false")
    boolean alwaysRefSubtypes();

    /**
     * Corresponds to {@code JacksonOption.INLINE_TRANSFORMED_SUBTYPES}.
     * <p>
     * A specialized option for handling subtypes that have been transformed.
     * </p>
     */
    @WithDefault("false")
    boolean inlineTransformedSubtypes();

    /**
     * Corresponds to {@code JacksonOption.SKIP_SUBTYPE_LOOKUP}.
     * <p>
     * If enabled, subtype resolution via {@code @JsonSubTypes} will be disabled entirely.
     * </p>
     */
    @WithDefault("false")
    boolean skipSubtypeLookup();

    /**
     * Corresponds to {@code JacksonOption.IGNORE_TYPE_INFO_TRANSFORM}.
     * <p>
     * If enabled, the transformation of the schema based on a {@code @JsonTypeInfo}
     * annotation will be skipped.
     * </p>
     */
    @WithDefault("false")
    boolean ignoreTypeInfoTransform();

    /**
     * Corresponds to {@code JacksonOption.JSONIDENTITY_REFERENCE_ALWAYS_AS_ID}.
     * <p>
     * If enabled, properties referencing an object that has an ID (via
     * {@code @JsonIdentityInfo}) will be represented as a simple ID field,
     * rather than a {@code $ref}.
     * </p>
     */
    @WithDefault("false")
    boolean jsonIdentityReferenceAlwaysAsId();
}
