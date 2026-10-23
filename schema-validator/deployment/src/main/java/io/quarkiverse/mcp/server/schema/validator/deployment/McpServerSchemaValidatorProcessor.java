package io.quarkiverse.mcp.server.schema.validator.deployment;

import io.quarkiverse.mcp.server.schema.validator.JsonSchemaValidator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;

public class McpServerSchemaValidatorProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-schema-validator");
    }

    @BuildStep
    void beans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.unremovableOf(JsonSchemaValidator.class));
    }

    @BuildStep
    void registerSchemaResources(BuildProducer<NativeImageResourcePatternsBuildItem> nativeImageResources) {
        // The JSON schemas are loaded as resources at runtime, i.e. they need to be registered for native mode
        // See also JsonSchemaValidator#loadSchema()
        nativeImageResources.produce(NativeImageResourcePatternsBuildItem.builder()
                .includeGlob("mcp_schema_*.json")
                .build());
    }

}
