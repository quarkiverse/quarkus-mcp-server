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

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.PromptCompleteManager;
import io.quarkiverse.mcp.server.runtime.PromptManager;
import io.quarkiverse.mcp.server.runtime.ResourceManager;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompleteManager;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManager;
import io.quarkiverse.mcp.server.runtime.ToolManager;
import io.quarkiverse.mcp.server.runtime.TrafficLogger;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

@Singleton
public class StdioMcpMessageHandler extends McpMessageHandler {

    private static final Logger LOG = Logger.getLogger(StdioMcpMessageHandler.class);

    private final ExecutorService executor;

    private final TrafficLogger trafficLogger;

    private final Vertx vertx;

    protected StdioMcpMessageHandler(McpRuntimeConfig config, ConnectionManager connectionManager, PromptManager promptManager,
            ToolManager toolManager, ResourceManager resourceManager, PromptCompleteManager promptCompleteManager,
            ResourceTemplateManager resourceTemplateManager, ResourceTemplateCompleteManager resourceTemplateCompleteManager,
            Vertx vertx) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager);
        this.executor = Executors.newSingleThreadExecutor();
        this.trafficLogger = config.trafficLogging().enabled() ? new TrafficLogger(config.trafficLogging().textLimit())
                : null;
        this.vertx = vertx;
    }

    void initialize(PrintStream stdout, McpRuntimeConfig config) {
        String connectionId = Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
        StdioMcpConnection connection = new StdioMcpConnection(connectionId, config.clientLogging().defaultLevel(),
                trafficLogger, config.autoPingInterval(), stdout, vertx);
        InputStream in = System.in;
        executor.submit(new Runnable() {

            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            JsonObject message;
                            try {
                                message = new JsonObject(line);
                            } catch (Exception e) {
                                String msg = "Unable to parse the JSON message";
                                LOG.errorf(e, msg);
                                connection.sendError(null, JsonRPC.PARSE_ERROR, msg);
                                return;
                            }
                            if (trafficLogger != null) {
                                trafficLogger.messageReceived(message);
                            }
                            if (JsonRPC.validate(message, connection)) {
                                handle(message, connection, connection);
                            }
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

}
