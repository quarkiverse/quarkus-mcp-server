package io.quarkiverse.mcp.server.stdio.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.InitialResponseInfo;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ContextSupport;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpMetrics;
import io.quarkiverse.mcp.server.runtime.McpRequestImpl;
import io.quarkiverse.mcp.server.runtime.NotificationManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResponseHandlers;
import io.quarkiverse.mcp.server.runtime.SecuritySupport;
import io.quarkiverse.mcp.server.runtime.Sender;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler.StdioMcpRequest;
import io.quarkus.arc.All;
import io.quarkus.runtime.Quarkus;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;

@Singleton
public class StdioMcpMessageHandler extends McpMessageHandler<StdioMcpRequest> {

    private static final Logger LOG = Logger.getLogger(StdioMcpMessageHandler.class);

    private final ExecutorService executor;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final McpServerRuntimeConfig serverConfig;

    protected StdioMcpMessageHandler(McpServersRuntimeConfig config, ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager, ResourceManagerImpl resourceManager, PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompletionManagerImpl resourceTemplateCompleteManager, NotificationManagerImpl initManager,
            ResponseHandlers responseHandlers,
            @All List<InitialCheck> initialChecks,
            @All List<InitialResponseInfo> initialResponseInfos,
            McpMetadata metadata,
            Vertx vertx,
            Instance<McpMetrics> metrics) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, initManager, responseHandlers, metadata, vertx,
                initialChecks, initialResponseInfos, metrics.isResolvable() ? metrics.get() : null);
        this.executor = Executors.newSingleThreadExecutor();
        if (config.servers().size() > 1) {
            throw new IllegalStateException("Multiple server configurations are not supported for the stdio transport");
        }
        this.serverConfig = config.servers().values().iterator().next();
    }

    public void initialize(PrintStream stdout) {
        if (initialized.compareAndSet(false, true)) {
            String connectionId = Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
            StdioMcpConnection connection = new StdioMcpConnection(connectionId, serverConfig, stdout, vertx);
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
                            Object json;
                            try {
                                json = Json.decodeValue(line);
                            } catch (Exception e) {
                                String msg = "Unable to parse the JSON message";
                                LOG.errorf(e, msg);
                                connection.sendError(null, JsonRpcErrorCodes.PARSE_ERROR, msg);
                                return;
                            }
                            Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
                            VertxContextSafetyToggle.setContextSafe(context, true);
                            context.executeBlocking(new Callable<>() {
                                @Override
                                public Object call() throws Exception {
                                    StdioMcpRequest mcpRequest = new StdioMcpRequest(McpServer.DEFAULT, json, connection,
                                            connection, null, null,
                                            null);
                                    handle(mcpRequest);
                                    return null;
                                }
                            });
                        }
                    } catch (IOException e) {
                        LOG.errorf(e, "Error reading stdio");
                    }
                }
            });
        }
    }

    @Override
    protected Transport transport() {
        return Transport.STDIO;
    }

    static class StdioMcpRequest extends McpRequestImpl<StdioMcpConnection> {

        StdioMcpRequest(String serverName, Object json, StdioMcpConnection connection, Sender sender,
                SecuritySupport securitySupport, ContextSupport requestContextSupport,
                CurrentIdentityAssociation currentIdentityAssociation) {
            super(serverName, json, connection, sender, securitySupport, requestContextSupport, currentIdentityAssociation);
        }

    }

}
