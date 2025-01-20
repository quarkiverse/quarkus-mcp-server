package io.quarkiverse.mcp.server.stdio.runtime;

import java.io.OutputStream;
import java.io.PrintStream;

import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.quarkiverse.mcp.server.stdio.runtime.config.McpStdioRuntimeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class StdioMcpServerRecorder {

    private final McpRuntimeConfig config;

    private final McpStdioRuntimeConfig stdioConfig;

    public StdioMcpServerRecorder(McpRuntimeConfig config, McpStdioRuntimeConfig stdioConfig) {
        this.config = config;
        this.stdioConfig = stdioConfig;
    }

    public void initialize() {
        PrintStream stdout = System.out;
        if (stdioConfig.nullSystemOut()) {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        }
        ArcContainer container = Arc.container();
        StdioMcpMessageHandler messageHandler = container.instance(StdioMcpMessageHandler.class).get();
        messageHandler.initialize(stdout, config);
    }

}
