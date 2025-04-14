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
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.InitManagerImpl;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompleteManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.TrafficLogger;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.quarkus.runtime.Quarkus;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

@Singleton
public class StdioMcpMessageHandler extends McpMessageHandler {

    private static final Logger LOG = Logger.getLogger(StdioMcpMessageHandler.class);

    private final ExecutorService executor;

    private final TrafficLogger trafficLogger;

    private final Vertx vertx;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    protected StdioMcpMessageHandler(McpRuntimeConfig config, ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager, ResourceManagerImpl resourceManager, PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompleteManagerImpl resourceTemplateCompleteManager, InitManagerImpl initManager,
            McpMetadata metadata,
            Vertx vertx) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, initManager, metadata);
        this.executor = Executors.newSingleThreadExecutor();
        this.trafficLogger = config.trafficLogging().enabled() ? new TrafficLogger(config.trafficLogging().textLimit())
                : null;
        this.vertx = vertx;
    }

    public void initialize(PrintStream stdout, McpRuntimeConfig config) {
        if (initialized.compareAndSet(false, true)) {
            String connectionId = Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
            StdioMcpConnection connection = new StdioMcpConnection(connectionId, config.clientLogging().defaultLevel(),
                    trafficLogger, config.autoPingInterval(), stdout, vertx);
            connectionManager.add(connection);
            InputStream in = System.in;
            executor.submit(new Runnable() {

                @Override
                public void run() {

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                        while (true) {
                            String line = reader.readLine();
                            if (line == null) {
                                LOG.debug("EOF received, exiting");
                                Quarkus.asyncExit(0);
                                return;
                            }
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
                                    trafficLogger.messageReceived(message, connection);
                                }
                                if (JsonRPC.validate(message, connection)) {
                                    handle(message, connection, connection, null);
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

}
