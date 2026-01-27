package io.quarkiverse.mcp.server.http.runtime;

import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.ELICITATION;
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

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.CompletionManager;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.PromptManager.PromptInfo;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpConnection.SubsidiarySse;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler.HttpMcpRequest;
import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServerRuntimeConfig;
import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServersRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ContextSupport;
import io.quarkiverse.mcp.server.runtime.FeatureArgument;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata;
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
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig.TrafficLogging;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.arc.All;
import io.quarkus.runtime.LaunchMode;
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
    private static final String CORS_ENABLED_PROPERTY = "quarkus.http.cors.enabled";
    private static final String CORS_ORIGINS_PROPERTY = "quarkus.http.cors.origins";

    public static final String MCP_SESSION_ID_HEADER = "Mcp-Session-Id";
    public static final String DUMMY_INIT_IMPL_NAME = "quarkus.mcp.http.streamable.dummy";

    private final McpMetadata metadata;

    private final CurrentVertxRequest currentVertxRequest;

    private final CurrentIdentityAssociation currentIdentityAssociation;

    private final McpHttpServersRuntimeConfig httpConfig;

    StreamableHttpMcpMessageHandler(McpServersRuntimeConfig config,
            McpHttpServersRuntimeConfig runtimeConfig,
            ConnectionManager connectionManager,
            PromptManagerImpl promptManager,
            ToolManagerImpl toolManager,
            ResourceManagerImpl resourceManager,
            PromptCompletionManagerImpl promptCompleteManager,
            ResourceTemplateManagerImpl resourceTemplateManager,
            ResourceTemplateCompletionManagerImpl resourceTemplateCompleteManager,
            NotificationManagerImpl notificationManager,
            ResponseHandlers responseHandlers,
            @All List<InitialCheck> initialChecks,
            CurrentVertxRequest currentVertxRequest,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            McpMetadata metadata,
            Vertx vertx) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, notificationManager, responseHandlers, metadata,
                vertx,
                initialChecks);
        this.metadata = metadata;
        this.currentVertxRequest = currentVertxRequest;
        this.currentIdentityAssociation = currentIdentityAssociation.isResolvable() ? currentIdentityAssociation.get() : null;
        this.httpConfig = runtimeConfig;
        checkCorsConfig(config);
    }

    @Override
    public void handle(RoutingContext ctx) {
        String serverName = ctx.get(HttpMcpServerRecorder.CONTEXT_KEY);
        if (serverName == null) {
            throw serverNameNotDefined();
        }
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

        StreamableHttpMcpConnection connection;
        String mcpSessionId = request.getHeader(MCP_SESSION_ID_HEADER);
        if (mcpSessionId == null) {
            connection = initConnection(serverName);
        } else {
            McpConnectionBase existing = connectionManager.get(mcpSessionId);
            if (existing == null) {
                if (httpConfig.servers().get(serverName).http().streamable().dummyInit()) {
                    // We don't care about non-existent session when dummy init is enabled
                    connection = initConnection(serverName);
                } else {
                    LOG.errorf("Mcp session not found: %s", mcpSessionId);
                    ctx.fail(404);
                    return;
                }
            } else if (existing instanceof StreamableHttpMcpConnection streamable) {
                connection = streamable;
            } else {
                throw new IllegalStateException("Invalid connection type: " + existing.getClass().getName());
            }
        }

        Object json;
        try {
            json = Json.decodeValue(ctx.body().buffer());
        } catch (Exception e) {
            String msg = "Unable to parse the JSON message";
            LOG.errorf(e, msg);
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.end(newError(null, JsonRpcErrorCodes.PARSE_ERROR, msg).toBuffer());
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

        HttpMcpRequest mcpRequest = new HttpMcpRequest(serverName, json, connection, securitySupport, ctx.response(),
                mcpSessionId == null,
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

            // Make sure the dummy connection is removed
            if (connection.initialRequest() != null
                    && DUMMY_INIT_IMPL_NAME.equals(connection.initialRequest().implementation().name())
                    && connectionManager.remove(connection.id())) {
                LOG.debugf("Dummy session removed [%s]", connection.id());
            }
        });
    }

    void openSseStream(RoutingContext ctx, ConnectionManager connectionManager, String serverName) {
        if (serverName == null) {
            throw serverNameNotDefined();
        }
        HttpServerRequest request = ctx.request();
        String mcpSessionId = request.getHeader(MCP_SESSION_ID_HEADER);
        if (mcpSessionId == null) {
            LOG.errorf("%s header not found", MCP_SESSION_ID_HEADER);
            ctx.fail(405);
            return;
        }
        McpConnectionBase connection = connectionManager.get(mcpSessionId);
        if (connection == null) {
            LOG.errorf("Mcp session not found: %s", mcpSessionId);
            ctx.fail(404);
            return;
        }

        HttpServerResponse response = ctx.response();
        response.setChunked(true);
        response.headers().add(HttpHeaders.CONTENT_TYPE, "text/event-stream");

        StreamableHttpMcpConnection streamableConnection = (StreamableHttpMcpConnection) connection;
        SubsidiarySse sse = new SubsidiarySse(ConnectionManager.connectionId(), response);
        streamableConnection.addSse(sse);

        // Send log notification to the client
        JsonObject log = Messages.newNotification(McpMessageHandler.NOTIFICATIONS_MESSAGE,
                Messages.newLog(McpLog.LogLevel.DEBUG, "SubsidiarySse",
                        "Subsidiary SSE opened [%s]".formatted(connection.id())));

        TrafficLogging trafficLogging = config.servers().get(serverName).trafficLogging();
        if (trafficLogging.enabled()) {
            TrafficLogger.messageSent(log, connection, trafficLogging.textLimit());
        }
        sse.sendEvent("message", log.encode());

        HttpMcpServerRecorder.setCloseHandler(request, () -> {
            if (streamableConnection.removeSse(sse.id())) {
                LOG.debugf("Subsidiary SSE [%s] stream closed [%s]", sse.id(), connection.id());
            }
        }, "subsidiary SSE will be closed upon session termination".formatted(connection.id()));

        LOG.debugf("Subsidiary SSE stream [%s] initialized [%s]", sse.id(), connection.id());
    }

    StreamableHttpMcpConnection initConnection(String serverName) {
        String id = ConnectionManager.connectionId();
        LOG.debugf("Streamable connection initialized [%s]", id);
        McpServerRuntimeConfig serverConfig = config.servers().get(serverName);
        StreamableHttpMcpConnection conn = new StreamableHttpMcpConnection(id, serverConfig);
        connectionManager.add(conn);
        return conn;
    }

    @Override
    protected InitialRequest dummyInitialRequest(HttpMcpRequest mcpRequest) {
        McpHttpServerRuntimeConfig config = httpConfig.servers().get(mcpRequest.serverName());
        if (config != null && config.http().streamable().dummyInit()) {
            return new InitialRequest(new Implementation(DUMMY_INIT_IMPL_NAME, "1", null), SUPPORTED_PROTOCOL_VERSIONS.get(0),
                    List.of(), transport());
        }
        return null;
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

    @Override
    protected Transport transport() {
        return Transport.STREAMABLE_HTTP;
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

    private static final Set<FeatureArgument.Provider> FORCE_SSE_PROVIDERS = Set.of(
            PROGRESS,
            MCP_LOG,
            SAMPLING,
            ROOTS,
            ELICITATION);

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
        if (method != null
                && Messages.isRequest(message)
                && FORCE_SSE_REQUESTS.contains(method)) {
            JsonObject params = Messages.getParams(message);
            if (params != null) {
                return switch (method) {
                    case TOOLS_CALL -> forceSseTool(params);
                    case PROMPTS_GET -> forceSsePrompt(params);
                    case RESOURCES_READ -> forceSseResource(params);
                    case COMPLETION_COMPLETE -> forceSseCompletion(params);
                    default -> throw new IllegalArgumentException("Unexpected value: " + method);
                };
            }
        }
        return false;
    }

    private boolean forceSseTool(JsonObject params) {
        String name = params.getString("name");
        if (name != null) {
            var fm = McpMetadata.findFeatureByName(metadata.tools(), name);
            if (fm != null) {
                for (FeatureArgument a : fm.info().arguments()) {
                    if (FORCE_SSE_PROVIDERS.contains(a.provider())) {
                        return true;
                    }
                }
            } else {
                ToolInfo info = toolManager.getTool(name);
                if (info != null && !info.isMethod()) {
                    // Always force SSE init for a tool added programatically
                    return true;
                }
            }
        }
        return false;
    }

    private boolean forceSsePrompt(JsonObject params) {
        String name = params.getString("name");
        if (name != null) {
            var fm = McpMetadata.findFeatureByName(metadata.prompts(), name);
            if (fm != null) {
                for (FeatureArgument a : fm.info().arguments()) {
                    if (FORCE_SSE_PROVIDERS.contains(a.provider())) {
                        return true;
                    }
                }
            } else {
                PromptInfo info = promptManager.getPrompt(name);
                if (info != null && !info.isMethod()) {
                    // Always force SSE init for a prompt added programatically
                    return true;
                }
            }
        }
        return false;
    }

    private boolean forceSseResource(JsonObject params) {
        String resourceUri = params.getString("uri");
        if (resourceUri != null) {
            FeatureMetadata<?> fm = metadata.resources().stream().filter(m -> m.info().uri().equals(resourceUri))
                    .findFirst().orElse(null);
            if (fm == null) {
                // Also try resource templates
                ResourceTemplateManager.ResourceTemplateInfo rti = resourceTemplateManager.findMatching(resourceUri);
                if (rti != null && rti.isMethod()) {
                    fm = McpMetadata.findFeatureByName(metadata.resourceTemplates(), rti.name());
                }
            }
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

    static class HttpMcpRequest extends McpRequestImpl<StreamableHttpMcpConnection> implements Sender {

        final boolean newSession;

        final AtomicBoolean sse;

        final HttpServerResponse response;

        public HttpMcpRequest(String serverName, Object json, StreamableHttpMcpConnection connection,
                SecuritySupport securitySupport,
                HttpServerResponse response, boolean newSession, ContextSupport contextSupport,
                CurrentIdentityAssociation currentIdentityAssociation) {
            super(serverName, json, connection, null, securitySupport, contextSupport, currentIdentityAssociation);
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
                // Forced SSE mode, such as @Tool method with "Sampling" param
                // "write" is async and synchronized over http connection, and should be thread-safe
                return response.write("event: message\ndata: " + message.encode() + "\n\n");
            } else {
                if (!Messages.isResponse(message)) {
                    // Try to use a subsidiary SSE
                    LOG.debugf("Not a response - try to use a subsidiary SSE channel instead");
                    return connection().send(message);
                } else {
                    response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                    return response.end(message.toBuffer());
                }
            }
        }

    }

    private static void checkCorsConfig(McpServersRuntimeConfig mcpConfig) {
        if (!mcpConfig.servers().isEmpty() && (LaunchMode.current().isProduction() || LaunchMode.current().isDev())) {
            final Config config = ConfigProvider.getConfig();
            if (!corsEnabled(config)) {
                LOG.warnf(
                        "Cross-Origin Resource Sharing (CORS) filter must be enabled for Streamable HTTP MCP server endpoints "
                                + " with `%s=true`",
                        CORS_ENABLED_PROPERTY);
                return;
            }
            if (corsOriginsEmpty(config)) {
                LOG.debugf("CORS filter allows single-origin requests only,"
                        + " use `%s` to accept requests from other trusted origins", CORS_ORIGINS_PROPERTY);
            }
        }

    }

    private static boolean corsEnabled(Config config) {
        return config.getOptionalValue(CORS_ENABLED_PROPERTY, Boolean.class).orElse(false);
    }

    private static boolean corsOriginsEmpty(Config config) {
        return config.getOptionalValues(CORS_ORIGINS_PROPERTY, String.class).orElse(List.of()).isEmpty();
    }

    private IllegalStateException serverNameNotDefined() {
        return new IllegalStateException("Server name not defined");
    }

}
