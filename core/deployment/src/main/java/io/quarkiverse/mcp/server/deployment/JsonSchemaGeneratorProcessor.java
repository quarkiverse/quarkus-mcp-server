package io.quarkiverse.mcp.server.deployment;

import io.quarkiverse.mcp.server.runtime.SchemaGeneratorConfigCustomizerJackson;
import io.quarkiverse.mcp.server.runtime.SchemaGeneratorConfigCustomizerJakartaValidation;
import io.quarkiverse.mcp.server.runtime.SchemaGeneratorConfigCustomizerSwagger2;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;

public class JsonSchemaGeneratorProcessor {

    public static final String MODULE_JACKSON = "jsonschema-module-jackson";
    public static final String MODULE_JAKARTA_VALIDATION = "jsonschema-module-jakarta-validation";
    public static final String MODULE_SWAGGER_2 = "jsonschema-module-swagger-2";
    public static final String VICTOOLS_GROUP_ID = "com.github.victools";

    @BuildStep
    void provideSchemaGeneratorAndOptionalModules(CurateOutcomeBuildItem curateOutcomeBuildItem,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        var appModel = curateOutcomeBuildItem.getApplicationModel();

        if (isDependencyPresent(appModel, MODULE_JACKSON)) {
            additionalBeanProducer.produce(new AdditionalBeanBuildItem(SchemaGeneratorConfigCustomizerJackson.class));
        }

        if (isDependencyPresent(appModel, MODULE_JAKARTA_VALIDATION)) {
            additionalBeanProducer
                    .produce(new AdditionalBeanBuildItem(SchemaGeneratorConfigCustomizerJakartaValidation.class));
        }

        if (isDependencyPresent(appModel, MODULE_SWAGGER_2)) {
            additionalBeanProducer.produce(new AdditionalBeanBuildItem(SchemaGeneratorConfigCustomizerSwagger2.class));
        }
    }

    private boolean isDependencyPresent(ApplicationModel applicationModel, String artifactId) {
        return applicationModel.getRuntimeDependencies()
                .stream()
                .anyMatch(dependency -> VICTOOLS_GROUP_ID.equals(dependency.getGroupId()) &&
                        artifactId.equals(dependency.getArtifactId()));
    }
}
