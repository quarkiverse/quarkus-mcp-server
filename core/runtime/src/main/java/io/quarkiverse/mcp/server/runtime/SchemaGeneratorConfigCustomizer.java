package io.quarkiverse.mcp.server.runtime;

import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;

/**
 * An interface that allows customization of the {@link SchemaGeneratorConfigBuilder}
 * before the {@link com.github.victools.jsonschema.generator.SchemaGenerator} is built.
 * <p>
 * Implementations of this interface will be discovered and applied automatically.
 */

public interface SchemaGeneratorConfigCustomizer {

    /**
     * Customizes the given {@link SchemaGeneratorConfigBuilder}.
     *
     * @param builder the builder to customize
     */
    void customize(SchemaGeneratorConfigBuilder builder);
}
