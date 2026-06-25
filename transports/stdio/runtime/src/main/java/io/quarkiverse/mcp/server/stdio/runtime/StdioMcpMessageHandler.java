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
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.InitialResponseInfo;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.runtime.CancellationRequests;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ContextSupport;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpMetrics;
import io.quarkiverse.mcp.server.runtime.McpRequestImpl;
import io.quarkiverse.mcp.server.runtime.McpRequestValidator;
import io.quarkiverse.mcp.server.runtime.McpTracing;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.runtime.NotificationManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.PromptManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateCompletionManagerImpl;
import io.quarkiverse.mcp.server.runtime.ResourceTemplateManagerImpl;
import io.quarkiverse.mcp.server.runtime.SecuritySupport;
import io.quarkiverse.mcp.server.runtime.Sender;
import io.quarkiverse.mcp.server.runtime.ServerRequests;
import io.quarkiverse.mcp.server.runtime.ToolManagerImpl;
import io.quarkiverse.mcp.server.runtime.TrafficListeners;
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
import io.vertx.core.json.JsonObject;

@Singleton
public class StdioMcpMessageHandler extends McpMessageHandler<StdioMcpRequest> {

    private static final Logger LOG = Logger.getLogger(StdioMcpMessageHandler.class);

    private final ExecutorService executor;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final McpServerRuntimeConfig serverConfig;

    private final TrafficListeners trafficListeners;

    protected StdioMcpMessageHandler(McpServersRuntimeConfig config, ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager, ResourceManagerImpl resourceManager, PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompletionManagerImpl resourceTemplateCompleteManager, NotificationManagerImpl initManager,
            ServerRequests serverRequests,
            CancellationRequests cancellationRequests,
            @All List<InitialCheck> initialChecks,
            @All List<InitialResponseInfo> initialResponseInfos,
            McpMetadata metadata,
            Vertx vertx,
            Instance<McpMetrics> metrics,
            Instance<McpTracing> tracing,
            Instance<McpRequestValidator> mcpRequestValidator,
            TrafficListeners trafficListeners) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, initManager, serverRequests, metadata, vertx,
                initialChecks, initialResponseInfos, metrics.isResolvable() ? metrics.get() : null,
                tracing.isResolvable() ? tracing.get() : null,
                mcpRequestValidator.isResolvable() ? mcpRequestValidator.get() : null, cancellationRequests);
        this.executor = Executors.newSingleThreadExecutor();
        if (config.servers().size() > 1) {
            throw new IllegalStateException("Multiple server configurations are not supported for the stdio transport");
        }
        this.serverConfig = config.servers().values().iterator().next();
        this.trafficListeners = trafficListeners;
    }

    public void initialize(PrintStream stdout) {
        if (initialized.compareAndSet(false, true)) {
            String connectionId = Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes());
            StdioMcpConnection connection = new StdioMcpConnection(connectionId, serverConfig, trafficListeners, stdout, vertx);
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
                            JsonObject message;
                            try {
                                message = (JsonObject) Json.decodeValue(line);
                            } catch (Exception e) {
                                String msg = "Unable to parse the JSON message";
                                LOG.warnf(e, msg);
                                connection.sendError(null, JsonRpcErrorCodes.PARSE_ERROR, msg);
                                return;
                            }
                            Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
                            VertxContextSafetyToggle.setContextSafe(context, true);
                            context.executeBlocking(new Callable<>() {
                                @Override
                                public Object call() throws Exception {
                                    StdioMcpConnection requestConnection;
                                    JsonObject meta = findMeta(message);
                                    if (isStatelessMessage(message, meta)) {
                                        requestConnection = new StdioMcpConnection(ConnectionManager.transientConnectionId(),
                                                serverConfig, trafficListeners, stdout, vertx, true);
                                        String metaVersion = meta != null
                                                ? meta.getString(MetaKey.PROTOCOL_VERSION.toString())
                                                : null;
                                        InitialRequest initialRequest;
                                        try {
                                            initialRequest = buildStatelessInitialRequest(meta, metaVersion,
                                                    Transport.STDIO);
                                            applyMetaLogLevel(meta, requestConnection);
                                        } catch (McpException e) {
                                            requestConnection.sendError(Messages.getId(message), e.getJsonRpcErrorCode(),
                                                    e.getMessage());
                                            return null;
                                        }
                                        requestConnection.initialize(initialRequest);
                                        requestConnection.setInitialized();
                                    } else {
                                        requestConnection = connection;
                                    }
                                    StdioMcpRequest mcpRequest = new StdioMcpRequest(McpServer.DEFAULT, message,
                                            requestConnection, requestConnection, null, null, null);
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

        StdioMcpRequest(String serverName, JsonObject message, StdioMcpConnection connection, Sender sender,
                SecuritySupport securitySupport, ContextSupport requestContextSupport,
                CurrentIdentityAssociation currentIdentityAssociation) {
            super(serverName, message, connection, sender, securitySupport, requestContextSupport, currentIdentityAssociation);
        }

    }

}
