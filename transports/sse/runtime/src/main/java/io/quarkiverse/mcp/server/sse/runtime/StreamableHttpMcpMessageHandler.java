package io.quarkiverse.mcp.server.sse.runtime;

import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.MCP_LOG;
import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.PROGRESS;
import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.ROOTS;
import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.SAMPLING;
import static io.quarkiverse.mcp.server.runtime.Messages.newError;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.CompletionManager;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.NotificationManager;
import io.quarkiverse.mcp.server.PromptManager.PromptInfo;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ContextSupport;
import io.quarkiverse.mcp.server.runtime.FeatureArgument;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.runtime.McpMetadata;
import io.quarkiverse.mcp.server.runtime.McpRequestImpl;
import io.quarkiverse.mcp.server.runtime.Messages;
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
import io.quarkiverse.mcp.server.runtime.TrafficLogger;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.HttpMcpRequest;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class StreamableHttpMcpMessageHandler extends McpMessageHandler<HttpMcpRequest> implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(StreamableHttpMcpMessageHandler.class);

    public static final String MCP_SESSION_ID_HEADER = "Mcp-Session-Id";

    private final McpMetadata metadata;

    private final CurrentVertxRequest currentVertxRequest;

    private final CurrentIdentityAssociation currentIdentityAssociation;

    StreamableHttpMcpMessageHandler(McpRuntimeConfig config,
            ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager,
            ResourceManagerImpl resourceManager,
            PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompletionManagerImpl resourceTemplateCompleteManager,
            NotificationManagerImpl notificationManager,
            ResponseHandlers serverRequests,
            CurrentVertxRequest currentVertxRequest,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            McpMetadata metadata,
            Vertx vertx) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, notificationManager, serverRequests, metadata, vertx);
        this.metadata = metadata;
        this.currentVertxRequest = currentVertxRequest;
        this.currentIdentityAssociation = currentIdentityAssociation.isResolvable() ? currentIdentityAssociation.get() : null;
    }

    @Override
    public void handle(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();

        // The client MUST include an "Accept" header,
        // listing both "application/json" and "text/event-stream" as supported content types
        List<String> accepts = ctx.request().headers().getAll(HttpHeaders.ACCEPT);
        if (!accepts(accepts, "application/json")
                || !accepts(accepts, "text/event-stream")) {
            LOG.errorf("Invalid Accept header: %s", accepts);
            ctx.fail(400);
            return;
        }

        McpConnectionBase connection;
        String mcpSessionId = request.getHeader(MCP_SESSION_ID_HEADER);
        if (mcpSessionId == null) {
            String id = ConnectionManager.connectionId();
            LOG.debugf("Streamable connection initialized [%s]", id);
            connection = new StreamableHttpMcpConnection(id, config.clientLogging().defaultLevel(),
                    config.trafficLogging().enabled() ? new TrafficLogger(config.trafficLogging().textLimit())
                            : null,
                    config.autoPingInterval());
            connectionManager.add(connection);
        } else {
            connection = connectionManager.get(mcpSessionId);
        }

        if (connection == null) {
            LOG.errorf("Mcp session not found: %s", mcpSessionId);
            ctx.fail(404);
            return;
        }

        Object json;
        try {
            json = Json.decodeValue(ctx.body().buffer());
        } catch (Exception e) {
            String msg = "Unable to parse the JSON message";
            LOG.errorf(e, msg);
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.end(newError(null, JsonRPC.PARSE_ERROR, msg).toBuffer());
            return;
        }
        QuarkusHttpUser user = (QuarkusHttpUser) ctx.user();
        SecuritySupport securitySupport = new SecuritySupport() {
            @Override
            public void setCurrentIdentity(CurrentIdentityAssociation currentIdentityAssociation) {
                if (user != null) {
                    SecurityIdentity identity = user.getSecurityIdentity();
                    currentIdentityAssociation.setIdentity(identity);
                } else {
                    currentIdentityAssociation.setIdentity(QuarkusHttpUser.getSecurityIdentity(ctx, null));
                }
            }
        };
        ContextSupport contextSupport = new ContextSupport() {
            @Override
            public void requestContextActivated() {
                currentVertxRequest.setCurrent(ctx);
            }
        };

        HttpMcpRequest mcpRequest = new HttpMcpRequest(json, connection, securitySupport, ctx.response(), mcpSessionId == null,
                contextSupport, currentIdentityAssociation);
        ScanResult result = scan(mcpRequest);
        if (result.forceSseInit()) {
            mcpRequest.initiateSse();
        }
        handle(mcpRequest).onComplete(ar -> {
            if (ar.succeeded()) {
                if (mcpRequest.sse.get()) {
                    // Just close the SSE stream
                    ctx.response().end();
                } else {
                    if (!ctx.response().ended()) {
                        if (!result.containsRequest()) {
                            // If the input consists solely of responses/notifications
                            // then the server MUST return HTTP status 202
                            ctx.response().setStatusCode(202).end();
                        } else {
                            ctx.end();
                        }
                    }
                }
            } else {
                if (!ctx.response().ended()) {
                    ctx.response().setStatusCode(500).end();
                }
            }
        });
    }

    public void terminateSession(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        String mcpSessionId = request.getHeader(MCP_SESSION_ID_HEADER);
        if (mcpSessionId == null) {
            LOG.errorf("Mcp session id header is missing: %s", ctx.normalizedPath());
            ctx.fail(404);
            return;
        }
        McpConnectionBase connection = connectionManager.get(mcpSessionId);
        if (connection == null) {
            LOG.errorf("Mcp session not found: %s", mcpSessionId);
            ctx.fail(404);
            return;
        }
        if (connectionManager.remove(connection.id())) {
            LOG.infof("Mcp session terminated: %s", connection.id());
        }
        ctx.end();
    }

    @Override
    protected void afterInitialize(HttpMcpRequest mcpRequest) {
        // Add the "Mcp-Session-Id" header to the response to the "Initialize" request
        mcpRequest.response.headers().add(MCP_SESSION_ID_HEADER, mcpRequest.connection().id());
    }

    @Override
    protected void initializeFailed(HttpMcpRequest mcpRequest) {
        connectionManager.remove(mcpRequest.connection().id());
    }

    @Override
    protected void jsonrpcValidationFailed(HttpMcpRequest mcpRequest) {
        if (mcpRequest.newSession) {
            connectionManager.remove(mcpRequest.connection().id());
        }
    }

    private boolean accepts(List<String> accepts, String contentType) {
        for (String accept : accepts) {
            if (accept.contains(contentType)) {
                return true;
            }
        }
        return false;
    }

    private static final Set<String> FORCE_SSE_REQUESTS = Set.of(
            TOOLS_CALL,
            PROMPTS_GET,
            RESOURCES_READ,
            COMPLETION_COMPLETE);

    private static final Set<String> FORCE_SSE_NOTIFICATIONS = Set.of(
            NOTIFICATIONS_INITIALIZED,
            NOTIFICATIONS_ROOTS_LIST_CHANGED);

    private static final Set<FeatureArgument.Provider> FORCE_SSE_PROVIDERS = Set.of(
            PROGRESS,
            MCP_LOG,
            SAMPLING,
            ROOTS);

    record ScanResult(boolean forceSseInit, boolean containsRequest) {
    }

    private ScanResult scan(HttpMcpRequest mcpRequest) {
        boolean forceSseInit = false;
        boolean containsRequest = false;
        // Scan the request payload and attempt to identify messages that should force SSE init
        // such as a tool call with the Progress param
        if (mcpRequest.json() instanceof JsonObject message) {
            forceSseInit = forceSse(mcpRequest, message);
            containsRequest = Messages.isRequest(message);
        } else if (mcpRequest.json() instanceof JsonArray batch) {
            if (!Messages.isResponse(batch.getJsonObject(0))) {
                // The batch contains at least 2 requests/notifications
                // or 1 requests/notification that forces SSE init
                forceSseInit = batch.size() > 1 || forceSse(mcpRequest, batch.getJsonObject(0));
                for (Object e : batch) {
                    if (e instanceof JsonObject message && Messages.isRequest(message)) {
                        containsRequest = true;
                        break;
                    }
                }
            }
        }
        return new ScanResult(forceSseInit, containsRequest);
    }

    private boolean forceSse(HttpMcpRequest mcpRequest, JsonObject message) {
        String method = message.getString("method");
        if (method != null) {
            if (Messages.isRequest(message) && FORCE_SSE_REQUESTS.contains(method)) {
                JsonObject params = message.getJsonObject("params");
                if (params != null) {
                    return switch (method) {
                        case TOOLS_CALL -> forceSseTool(params);
                        case PROMPTS_GET -> forceSsePrompt(params);
                        case RESOURCES_READ -> forceSseResource(params);
                        case COMPLETION_COMPLETE -> forceSseCompletion(params);
                        default -> throw new IllegalArgumentException("Unexpected value: " + method);
                    };
                }
            } else if (Messages.isNotification(message)
                    && FORCE_SSE_NOTIFICATIONS.contains(method)
                    && forceSseNotification(method)) {
                return true;
            }
        }
        return false;
    }

    private boolean forceSseTool(JsonObject params) {
        String toolName = params.getString("name");
        FeatureMetadata<?> fm = metadata.tools().stream().filter(m -> m.info().name().equals(toolName))
                .findFirst().orElse(null);
        if (fm != null) {
            for (FeatureArgument a : fm.info().arguments()) {
                if (FORCE_SSE_PROVIDERS.contains(a.provider())) {
                    return true;
                }
            }
        } else {
            ToolInfo info = toolManager.getTool(toolName);
            if (info != null && !info.isMethod()) {
                // Always force SSE init for a tool added programatically
                return true;
            }
        }
        return false;
    }

    private boolean forceSsePrompt(JsonObject params) {
        String promptName = params.getString("name");
        FeatureMetadata<?> fm = metadata.prompts().stream().filter(m -> m.info().name().equals(promptName))
                .findFirst().orElse(null);
        if (fm != null) {
            for (FeatureArgument a : fm.info().arguments()) {
                if (FORCE_SSE_PROVIDERS.contains(a.provider())) {
                    return true;
                }
            }
        } else {
            PromptInfo info = promptManager.getPrompt(promptName);
            if (info != null && !info.isMethod()) {
                // Always force SSE init for a prompt added programatically
                return true;
            }
        }
        return false;
    }

    private boolean forceSseResource(JsonObject params) {
        String resourceUri = params.getString("uri");
        FeatureMetadata<?> fm = metadata.resources().stream().filter(m -> m.info().uri().equals(resourceUri))
                .findFirst().orElse(null);
        if (fm != null) {
            for (FeatureArgument a : fm.info().arguments()) {
                if (FORCE_SSE_PROVIDERS.contains(a.provider())) {
                    return true;
                }
            }
        } else {
            ResourceManager.ResourceInfo info = resourceManager.getResource(resourceUri);
            if (info != null) {
                if (!info.isMethod()) {
                    // Always force SSE init for a resource added programatically
                    return true;
                }
            } else {
                // Also try resource templates
                ResourceTemplateManager.ResourceTemplateInfo rti = resourceTemplateManager.findMatching(resourceUri);
                if (rti != null && !rti.isMethod()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean forceSseCompletion(JsonObject params) {
        JsonObject ref = params.getJsonObject("ref");
        if (ref != null) {
            String referenceType = ref.getString("type");
            String referenceName = ref.getString("name");
            JsonObject argument = params.getJsonObject("argument");
            String argumentName = argument != null ? argument.getString("name") : null;
            if (referenceName != null && argumentName != null) {
                if ("ref/prompt".equals(referenceType)) {
                    return forceSseCompletion(referenceName, argumentName, metadata.promptCompletions(),
                            promptCompletionManager);
                } else if ("ref/resource".equals(referenceType)) {
                    return forceSseCompletion(referenceName, argumentName, metadata.resourceTemplateCompletions(),
                            resourceTemplateCompletionManager);
                }
            }
        }
        return false;

    }

    private boolean forceSseCompletion(String referenceName, String argumentName,
            List<FeatureMetadata<CompletionResponse>> completions, CompletionManager completionManager) {
        FeatureMetadata<?> fm = completions.stream().filter(m -> {
            return m.info().name().equals(referenceName)
                    && argumentName.equals(m.info().arguments().stream().filter(FeatureArgument::isParam).findFirst()
                            .orElseThrow().name());
        })
                .findFirst().orElse(null);
        if (fm != null) {
            for (FeatureArgument a : fm.info().arguments()) {
                if (FORCE_SSE_PROVIDERS.contains(a.provider())) {
                    return true;
                }
            }
        } else {
            CompletionManager.CompletionInfo info = completionManager.getCompletion(referenceName, argumentName);
            if (info != null && !info.isMethod()) {
                // Always force SSE init for a completion added programatically
                return true;
            }
        }
        return false;
    }

    private boolean forceSseNotification(String method) {
        List<FeatureMetadata<Void>> fm = metadata.notifications()
                .stream()
                .filter(m -> Notification.Type.valueOf(m.info().description()) == Notification.Type.from(method))
                .toList();
        for (FeatureMetadata<?> m : fm) {
            for (FeatureArgument a : m.info().arguments()) {
                if (FORCE_SSE_PROVIDERS.contains(a.provider())) {
                    return true;
                }
            }
        }
        for (NotificationManager.NotificationInfo info : notificationManager) {
            if (!info.isMethod() && info.type() == Type.from(method)) {
                // Always force SSE init for a notification added programatically
                return true;
            }
        }
        return false;
    }

    static class HttpMcpRequest extends McpRequestImpl implements Sender {

        final boolean newSession;

        final AtomicBoolean sse;

        final HttpServerResponse response;

        public HttpMcpRequest(Object json, McpConnectionBase connection, SecuritySupport securitySupport,
                HttpServerResponse response, boolean newSession, ContextSupport contextSupport,
                CurrentIdentityAssociation currentIdentityAssociation) {
            super(json, connection, null, securitySupport, contextSupport, currentIdentityAssociation);
            this.newSession = newSession;
            this.sse = new AtomicBoolean(false);
            this.response = response;
        }

        @Override
        public Sender sender() {
            return this;
        }

        boolean initiateSse() {
            if (sse.compareAndSet(false, true)) {
                response.setChunked(true);
                response.headers().add(HttpHeaders.CONTENT_TYPE, "text/event-stream");
                return true;
            }
            return false;
        }

        @Override
        public Future<Void> send(JsonObject message) {
            if (message == null) {
                return Future.succeededFuture();
            }
            messageSent(message);
            if (sse.get()) {
                // "write" is async and synchronized over http connection, and should be thread-safe
                return response.write("event: message\ndata: " + message.encode() + "\n\n");
            } else {
                response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                return response.end(message.toBuffer());
            }
        }

    }

}
