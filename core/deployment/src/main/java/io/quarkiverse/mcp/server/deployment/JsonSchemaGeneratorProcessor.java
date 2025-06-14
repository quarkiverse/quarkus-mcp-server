package io.quarkiverse.mcp.server.deployment;

import java.util.List;

import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;

import io.quarkiverse.mcp.server.runtime.SchemaGeneratorConfigCustomizerJackson;
import io.quarkiverse.mcp.server.runtime.SchemaGeneratorConfigCustomizerJakartaValidation;
import io.quarkiverse.mcp.server.runtime.SchemaGeneratorConfigCustomizerSwagger2;
import io.quarkiverse.mcp.server.runtime.SchemaGeneratorProvider;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;

public class JsonSchemaGeneratorProcessor {

    @BuildStep
    void indexVictoolsModules(BuildProducer<IndexDependencyBuildItem> indexDependencyProducer) {
        var dependenciesToIndex = List.of(
                new IndexDependencyBuildItem("com.github.victools", "jsonschema-module-jackson"),
                new IndexDependencyBuildItem("com.github.victools", "jsonschema-module-jakarta-validation"),
                new IndexDependencyBuildItem("com.github.victools", "jsonschema-module-swagger-2"));
        indexDependencyProducer.produce(dependenciesToIndex);
    }

    @BuildStep
    void provideSchemaGeneratorAndOptionalModules(BuildProducer<FeatureBuildItem> featureBuildItemProducer,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {

        additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf(SchemaGeneratorProvider.class));

        if (combinedIndex.getIndex().getClassByName(JacksonModule.class.getName()) != null) {
            additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf(SchemaGeneratorConfigCustomizerJackson.class));
            featureBuildItemProducer.produce(new FeatureBuildItem("mcp-server-schemagen-jackson"));
        }

        if (combinedIndex.getIndex().getClassByName(JakartaValidationModule.class.getName()) != null) {
            additionalBeanProducer
                    .produce(AdditionalBeanBuildItem.unremovableOf(SchemaGeneratorConfigCustomizerJakartaValidation.class));
            featureBuildItemProducer.produce(new FeatureBuildItem("mcp-server-schemagen-jakarta-validation"));
        }

        if (combinedIndex.getIndex().getClassByName(Swagger2Module.class.getName()) != null) {
            additionalBeanProducer
                    .produce(AdditionalBeanBuildItem.unremovableOf(SchemaGeneratorConfigCustomizerSwagger2.class));
            featureBuildItemProducer.produce(new FeatureBuildItem("mcp-server-schemagen-swagger2"));
        }
    }
}
