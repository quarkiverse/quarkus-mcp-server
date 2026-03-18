package io.quarkiverse.mcp.server.schema.validator.deployment;

import io.quarkiverse.mcp.server.schema.validator.JsonSchemaValidator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class McpServerSchemaValidatorProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-schema-validator");
    }

    @BuildStep
    void beans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.unremovableOf(JsonSchemaValidator.class));
    }

}
