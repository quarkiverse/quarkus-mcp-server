package io.quarkiverse.mcp.server.stdio.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import io.quarkiverse.mcp.server.deployment.SelectedTransportBuildItem;
import io.quarkiverse.mcp.server.deployment.TransportCandidateBuildItem;
import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpServerRecorder;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
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
    TransportCandidateBuildItem transportCandidate() {
        return new TransportCandidateBuildItem("stdio");
    }

    @Record(RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    @BuildStep
    void initialize(SelectedTransportBuildItem selectedTransportCandidateBuildItem, StdioMcpServerRecorder recorder) {
        if (!selectedTransportCandidateBuildItem.getName().equals("stdio")) {
            return;
        }
        recorder.initialize();
    }

}
