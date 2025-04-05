package io.quarkiverse.mcp.server.stdio.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.util.List;

import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler;
import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpServerRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class StdioMcpServerProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-stdio");
    }

    @BuildStep
    void addBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(StdioMcpMessageHandler.class));
    }

    @Record(RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    @BuildStep
    void initialize(StdioMcpServerRecorder recorder, McpStdioBuildTimeConfig config,
            List<McpStdioExplicitInitializationBuildItem> initialization) {
        if (config.initializationEnabled() && initialization.isEmpty()) {
            recorder.initialize();
        }
    }

}
