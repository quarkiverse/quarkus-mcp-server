package io.quarkiverse.mcp.server.tasks.deployment;

import java.util.Set;

import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.Feature;
import io.quarkiverse.mcp.server.deployment.FeatureArgumentProviderBuildItem;
import io.quarkiverse.mcp.server.tasks.Tasks;
import io.quarkiverse.mcp.server.tasks.runtime.TaskManagerImpl;
import io.quarkiverse.mcp.server.tasks.runtime.TasksArgumentProvider;
import io.quarkiverse.mcp.server.tasks.runtime.TasksExtension;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class McpTasksProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-tasks");
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .setUnremovable()
                .addBeanClasses(TaskManagerImpl.class, TasksExtension.class, TasksArgumentProvider.class)
                .build());
    }

    @BuildStep
    FeatureArgumentProviderBuildItem tasksArgument() {
        // The Tasks parameter may only be declared by tool methods
        return new FeatureArgumentProviderBuildItem(DotName.createSimple(Tasks.class), TasksArgumentProvider.class.getName(),
                Set.of(Feature.TOOL));
    }

}
