package io.quarkiverse.mcp.server.stdio.runtime;

import java.io.OutputStream;
import java.io.PrintStream;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.stdio.runtime.config.McpStdioRuntimeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class StdioMcpServerRecorder {

    private static final Logger LOG = Logger.getLogger(StdioMcpServerRecorder.class);

    private final McpStdioRuntimeConfig stdioConfig;

    public StdioMcpServerRecorder(McpStdioRuntimeConfig stdioConfig) {
        this.stdioConfig = stdioConfig;
    }

    public void initialize() {
        if (stdioConfig.enabled()) {
            PrintStream stdout = System.out;
            if (stdioConfig.nullSystemOut()) {
                System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            }
            ArcContainer container = Arc.container();
            StdioMcpMessageHandler messageHandler = container.instance(StdioMcpMessageHandler.class).get();
            messageHandler.initialize(stdout);
        } else {
            LOG.info(
                    "stdio transport is disabled - application does not read/write MCP messages from/to the standard input/output");
        }
    }

}
