package io.quarkiverse.mcp.server.runtime;

import jakarta.enterprise.context.Dependent;

import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;

import io.quarkiverse.mcp.server.runtime.config.McpServerSchemaGeneratorSwagger2RuntimeConfig;

@Dependent
public class SchemaGeneratorConfigCustomizerSwagger2 implements SchemaGeneratorConfigCustomizer {

    private final McpServerSchemaGeneratorSwagger2RuntimeConfig config;

    public SchemaGeneratorConfigCustomizerSwagger2(McpServerSchemaGeneratorSwagger2RuntimeConfig config) {
        this.config = config;
    }

    @Override
    public void customize(SchemaGeneratorConfigBuilder builder) {
        if (config.enabled()) {
            var swagger2Module = createSwagger2Module();
            builder.with(swagger2Module);
        }
    }

    Module createSwagger2Module() {
        return new Swagger2Module();
    }
}
