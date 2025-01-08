package io.quarkiverse.mcp.server.stdio.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.PromptManager;
import io.quarkiverse.mcp.server.runtime.ResourceManager;
import io.quarkiverse.mcp.server.runtime.Responder;
import io.quarkiverse.mcp.server.runtime.ToolManager;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

class StdioMcpMessageHandler extends McpMessageHandler {

    private static final Logger LOG = Logger.getLogger(StdioMcpMessageHandler.class);

    private final ExecutorService executor;

    protected StdioMcpMessageHandler(McpRuntimeConfig config, ConnectionManager connectionManager, PromptManager promptManager,
            ToolManager toolManager, ResourceManager resourceManager) {
        super(config, connectionManager, promptManager, toolManager, resourceManager);
        this.executor = Executors.newSingleThreadExecutor();
    }

    void initialize(PrintStream stdout) {
        StdioResponder responder = new StdioResponder(stdout);
        StdioMcpConnection connection = new StdioMcpConnection(
                Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes()));
        InputStream in = System.in;
        executor.submit(new Runnable() {

            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        JsonObject message;
                        try {
                            message = new JsonObject(line);
                            handle(message, connection, responder);
                        } catch (DecodeException e) {
                            String msg = "Unable to parse the JSON message";
                            LOG.errorf(e, msg);
                        }
                    }
                } catch (IOException e) {
                    LOG.errorf(e, "Error reading stdio");
                }
            }
        });
    }

    class StdioResponder implements Responder {

        final PrintStream out;

        StdioResponder(PrintStream out) {
            this.out = out;
        }

        @Override
        public void send(JsonObject message) {
            if (message == null) {
                return;
            }
            out.println(message.encode());
        }

    }

}
