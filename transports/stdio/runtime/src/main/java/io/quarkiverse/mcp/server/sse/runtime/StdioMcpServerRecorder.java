package io.quarkiverse.mcp.server.sse.runtime;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.PromptManager;
import io.quarkiverse.mcp.server.runtime.ResourceManager;
import io.quarkiverse.mcp.server.runtime.ToolManager;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class StdioMcpServerRecorder {

    private final McpRuntimeConfig config;

    public StdioMcpServerRecorder(McpRuntimeConfig config) {
        this.config = config;
    }

    public void initialize() {
        ArcContainer container = Arc.container();
        StdioMcpMessageHandler messageHandler = new StdioMcpMessageHandler(config,
                container.instance(ConnectionManager.class).get(),
                container.instance(PromptManager.class).get(), container.instance(ToolManager.class).get(),
                container.instance(ResourceManager.class).get());
        messageHandler.initialize();
    }

}
