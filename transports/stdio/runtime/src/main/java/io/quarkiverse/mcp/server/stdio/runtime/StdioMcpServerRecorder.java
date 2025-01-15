package io.quarkiverse.mcp.server.stdio.runtime;

import java.io.OutputStream;
import java.io.PrintStream;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.PromptCompleteManager;
import io.quarkiverse.mcp.server.runtime.PromptManager;
import io.quarkiverse.mcp.server.runtime.ResourceManager;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManager;
import io.quarkiverse.mcp.server.runtime.ToolManager;
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
        StdioMcpMessageHandler messageHandler = new StdioMcpMessageHandler(config,
                container.instance(ConnectionManager.class).get(),
                container.instance(PromptManager.class).get(), container.instance(ToolManager.class).get(),
                container.instance(ResourceManager.class).get(), container.instance(PromptCompleteManager.class).get(),
                container.instance(ResourceTemplateManager.class).get());
        messageHandler.initialize(stdout);
    }

}
