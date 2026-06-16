package io.quarkiverse.mcp.server.http.runtime;

import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.ELICITATION;
import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.MCP_LOG;
import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.PROGRESS;
import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.ROOTS;
import static io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider.SAMPLING;
import static io.quarkiverse.mcp.server.runtime.Messages.newError;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.CompletionManager;
import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.InitialResponseInfo;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.PromptManager.PromptInfo;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.TransportHint;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpConnection.SseStream;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler.HttpMcpRequest;
import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServerRuntimeConfig;
import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServersRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.CancellationRequests;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.ContextSupport;
import io.quarkiverse.mcp.server.runtime.FeatureArgument;
import io.quarkiverse.mcp.server.runtime.FeatureKey;
import io.quarkiverse.mcp.server.runtime.FeatureMetadata;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
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
import io.quarkiverse.mcp.server.runtime.Subscription;
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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class StreamableHttpMcpMessageHandler extends McpMessageHandler<HttpMcpRequest> implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(StreamableHttpMcpMessageHandler.class);
    private static final String CORS_ENABLED_PROPERTY = "quarkus.http.cors.enabled";
    private static final String CORS_ORIGINS_PROPERTY = "quarkus.http.cors.origins";

    public static final String MCP_SESSION_ID_HEADER = "Mcp-Session-Id";
    public static final String MCP_PROTOCOL_VERSION_HEADER = "Mcp-Protocol-Version";
    public static final String MCP_METHOD_HEADER = "Mcp-Method";
    public static final String MCP_NAME_HEADER = "Mcp-Name";
    public static final String AUTO_INIT_IMPL_NAME = "quarkus.mcp.http.streamable.dummy";
    private static final Implementation AUTO_INIT_IMPLEMENTATION = new Implementation(AUTO_INIT_IMPL_NAME, "1", null);

    private final CurrentVertxRequest currentVertxRequest;
    private final CurrentIdentityAssociation currentIdentityAssociation;
    private final McpHttpServersRuntimeConfig httpConfig;

    // Precomputed maps for eager SSE init (lazySseInit=false), null when lazy
    private final Map<FeatureKey, Boolean> toolsForceSse;
    private final Map<FeatureKey, Boolean> promptsForceSse;
    private final Map<FeatureKey, Boolean> resourcesForceSse;
    private final Map<FeatureKey, Boolean> resourceTemplatesForceSse;
    private final Map<String, Boolean> completionsForceSse;

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
            ServerRequests serverRequests,
            CancellationRequests cancellationRequests,
            @All List<InitialCheck> initialChecks,
            @All List<InitialResponseInfo> initialResponseInfos,
            CurrentVertxRequest currentVertxRequest,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            McpMetadata metadata,
            Vertx vertx,
            Instance<McpMetrics> metrics,
            Instance<McpTracing> tracing,
            Instance<McpRequestValidator> mcpRequestValidator) {
        super(config, connectionManager, promptManager, toolManager, resourceManager, promptCompleteManager,
                resourceTemplateManager, resourceTemplateCompleteManager, notificationManager, serverRequests,
                metadata,
                vertx, initialChecks, initialResponseInfos, metrics.isResolvable() ? metrics.get() : null,
                tracing.isResolvable() ? tracing.get() : null,
                mcpRequestValidator.isResolvable() ? mcpRequestValidator.get() : null, cancellationRequests);
        this.currentVertxRequest = currentVertxRequest;
        this.currentIdentityAssociation = currentIdentityAssociation.isResolvable() ? currentIdentityAssociation.get() : null;
        this.httpConfig = runtimeConfig;
        checkCorsConfig(config);

        // Precompute forceSse maps only when eager SSE init is configured
        if (hasEagerSseInit(runtimeConfig)) {
            this.toolsForceSse = precomputeForceSse(metadata.tools());
            this.promptsForceSse = precomputeForceSse(metadata.prompts());
            this.resourcesForceSse = precomputeForceSse(metadata.resources(), fm -> fm.info().uri());
            this.resourceTemplatesForceSse = precomputeForceSse(metadata.resourceTemplates());
            this.completionsForceSse = precomputeCompletionForceSse(
                    Stream.concat(metadata.promptCompletions().stream(), metadata.resourceTemplateCompletions().stream())
                            .toList());
        } else {
            this.toolsForceSse = null;
            this.promptsForceSse = null;
            this.resourcesForceSse = null;
            this.resourceTemplatesForceSse = null;
            this.completionsForceSse = null;
        }
    }

    private static boolean hasEagerSseInit(McpHttpServersRuntimeConfig runtimeConfig) {
        for (var config : runtimeConfig.servers().values()) {
            if (!config.http().streamable().lazySseInit()) {
                return true;
            }
        }
        return false;
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
            LOG.warnf("Invalid Accept header: %s", accepts);
            ctx.fail(400);
            return;
        }

        JsonObject message;
        try {
            message = (JsonObject) Json.decodeValue(ctx.body().buffer());
        } catch (Exception e) {
            String msg = "Unable to parse the JSON message";
            LOG.warnf(e, msg);
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.end(newError(null, JsonRpcErrorCodes.PARSE_ERROR, msg).toBuffer());
            return;
        }

        String mcpProtocolVersion = request.getHeader(MCP_PROTOCOL_VERSION_HEADER);
        JsonObject meta = findMeta(message);
        if (isStatelessRequest(mcpProtocolVersion, message, meta)) {
            handleStateless(ctx, serverName, request, message, mcpProtocolVersion, meta);
        } else {
            handleStateful(ctx, serverName, request, message, mcpProtocolVersion);
        }
    }

    private boolean isStatelessRequest(String mcpProtocolVersion, JsonObject message, JsonObject meta) {
        if (mcpProtocolVersion != null && isStateless(mcpProtocolVersion)) {
            return true;
        }
        return isStatelessMessage(message, meta);
    }

    private void handleStateless(RoutingContext ctx, String serverName, HttpServerRequest request,
            JsonObject message, String mcpProtocolVersion, JsonObject meta) {

        // Validate required headers for stateless clients
        if (!validateStatelessHeaders(ctx, message, mcpProtocolVersion, meta)) {
            return;
        }

        // Validate protocol version is supported
        if (mcpProtocolVersion != null && !McpProtocolVersion.SUPPORTED_VERSIONS.contains(mcpProtocolVersion)) {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.response().setStatusCode(400);
            ctx.end(newError(null, JsonRpcErrorCodes.UNSUPPORTED_PROTOCOL_VERSION,
                    "Unsupported protocol version",
                    new JsonObject().put("supported", McpProtocolVersion.SUPPORTED_VERSIONS).put("requested",
                            mcpProtocolVersion))
                    .toBuffer());
            return;
        }

        // Create a transient connection — not registered in ConnectionManager
        StreamableHttpMcpConnection connection = createTransientConnection(serverName);

        // Build InitialRequest from _meta and auto-initialize
        InitialRequest initialRequest;
        try {
            initialRequest = buildStatelessInitialRequest(meta, mcpProtocolVersion,
                    Transport.STREAMABLE_HTTP);
            applyMetaLogLevel(meta, connection);
        } catch (McpException e) {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.response().setStatusCode(400);
            ctx.end(newError(Messages.getId(message), e.getJsonRpcErrorCode(), e.getMessage()).toBuffer());
            return;
        }
        connection.initialize(initialRequest);
        connection.setInitialized();

        QuarkusHttpUser user = (QuarkusHttpUser) ctx.user();
        SecuritySupport securitySupport = createSecuritySupport(ctx, user);
        ContextSupport contextSupport = createContextSupport(ctx);

        boolean lazySseInit = computeLazySseInit(serverName, message);
        HttpMcpRequest mcpRequest = new HttpMcpRequest(serverName, message, connection, securitySupport,
                ctx.request(), ctx.response(),
                true, contextSupport, currentIdentityAssociation, mcpProtocolVersion, lazySseInit);

        try {
            boolean containsRequest = scan(mcpRequest);
            handle(mcpRequest).onComplete(ar -> {
                completeResponse(ctx, mcpRequest, containsRequest, ar.succeeded());
            });
        } catch (Exception e) {
            throw e;
        }
    }

    private boolean validateStatelessHeaders(RoutingContext ctx, JsonObject message, String mcpProtocolVersion,
            JsonObject meta) {
        // Validate Mcp-Method header
        String mcpMethodHeader = ctx.request().getHeader(MCP_METHOD_HEADER);
        String bodyMethod = message.getString("method");
        if (mcpMethodHeader == null) {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.response().setStatusCode(400);
            ctx.end(newError(null, JsonRpcErrorCodes.HEADER_MISMATCH,
                    "Missing required header: " + MCP_METHOD_HEADER).toBuffer());
            return false;
        }
        if (bodyMethod != null && !mcpMethodHeader.equals(bodyMethod)) {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.response().setStatusCode(400);
            ctx.end(newError(null, JsonRpcErrorCodes.HEADER_MISMATCH,
                    "Header mismatch: " + MCP_METHOD_HEADER + " header value '" + mcpMethodHeader
                            + "' does not match body value '" + bodyMethod + "'")
                    .toBuffer());
            return false;
        }

        // Validate Mcp-Name header for methods that require it
        if ("tools/call".equals(bodyMethod) || "prompts/get".equals(bodyMethod)) {
            JsonObject params = Messages.getParams(message);
            String bodyName = params != null ? params.getString("name") : null;
            if (!validateMcpNameHeader(ctx, bodyName)) {
                return false;
            }
        } else if ("resources/read".equals(bodyMethod)) {
            JsonObject params = Messages.getParams(message);
            String bodyUri = params != null ? params.getString("uri") : null;
            if (!validateMcpNameHeader(ctx, bodyUri)) {
                return false;
            }
        }

        // Validate MCP-Protocol-Version header matches _meta version
        if (meta != null && mcpProtocolVersion != null) {
            String metaVersion = meta.getString(MetaKey.PROTOCOL_VERSION.toString());
            if (metaVersion != null && !mcpProtocolVersion.equals(metaVersion)) {
                ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                ctx.response().setStatusCode(400);
                ctx.end(newError(null, JsonRpcErrorCodes.HEADER_MISMATCH,
                        "Header mismatch: " + MCP_PROTOCOL_VERSION_HEADER + " header value '" + mcpProtocolVersion
                                + "' does not match _meta value '" + metaVersion + "'")
                        .toBuffer());
                return false;
            }
        }
        return true;
    }

    private boolean validateMcpNameHeader(RoutingContext ctx, String expectedValue) {
        String mcpNameHeader = ctx.request().getHeader(MCP_NAME_HEADER);
        if (mcpNameHeader == null) {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.response().setStatusCode(400);
            ctx.end(newError(null, JsonRpcErrorCodes.HEADER_MISMATCH,
                    "Missing required header: " + MCP_NAME_HEADER).toBuffer());
            return false;
        }
        if (expectedValue != null && !mcpNameHeader.equals(expectedValue)) {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            ctx.response().setStatusCode(400);
            ctx.end(newError(null, JsonRpcErrorCodes.HEADER_MISMATCH,
                    "Header mismatch: " + MCP_NAME_HEADER + " header value '" + mcpNameHeader
                            + "' does not match body value '" + expectedValue + "'")
                    .toBuffer());
            return false;
        }
        return true;
    }

    private StreamableHttpMcpConnection createTransientConnection(String serverName) {
        String id = ConnectionManager.transientConnectionId();
        LOG.debugf("Stateless transient connection created [%s]", id);
        McpServerRuntimeConfig serverConfig = config.servers().get(serverName);
        return new StreamableHttpMcpConnection(id, serverConfig, serverName, true);
    }

    private void handleStateful(RoutingContext ctx, String serverName, HttpServerRequest request,
            JsonObject message, String mcpProtocolVersion) {

        StreamableHttpMcpConnection connection;
        String mcpSessionId = request.getHeader(MCP_SESSION_ID_HEADER);
        if (mcpSessionId == null) {
            connection = initConnection(serverName);
        } else {
            McpConnectionBase existing = connectionManager.get(mcpSessionId);
            if (existing == null) {
                if (httpConfig.servers().get(serverName).http().streamable().autoInit()) {
                    connection = initConnection(serverName);
                } else {
                    LOG.warnf("Mcp session not found: %s", mcpSessionId);
                    ctx.fail(404);
                    return;
                }
            } else if (existing instanceof StreamableHttpMcpConnection streamable) {
                connection = streamable;
            } else {
                throw new IllegalStateException("Invalid connection type: " + existing.getClass().getName());
            }
        }

        if (mcpProtocolVersion != null
                && !McpProtocolVersion.SUPPORTED_VERSIONS.contains(mcpProtocolVersion)) {
            LOG.warnf("Invalid MCP protocol header: %s", mcpProtocolVersion);
            ctx.fail(400);
            return;
        }

        QuarkusHttpUser user = (QuarkusHttpUser) ctx.user();
        SecuritySupport securitySupport = createSecuritySupport(ctx, user);
        ContextSupport contextSupport = createContextSupport(ctx);

        boolean lazySseInit = computeLazySseInit(serverName, message);
        HttpMcpRequest mcpRequest = new HttpMcpRequest(serverName, message, connection, securitySupport,
                ctx.request(), ctx.response(),
                mcpSessionId == null, contextSupport, currentIdentityAssociation, mcpProtocolVersion, lazySseInit);
        try {
            boolean containsRequest = scan(mcpRequest);
            handle(mcpRequest).onComplete(ar -> {
                completeResponse(ctx, mcpRequest, containsRequest, ar.succeeded());
                removeAutoInitConnection(connection);
            });
        } catch (Exception e) {
            removeAutoInitConnection(connection);
            throw e;
        }
    }

    private SecuritySupport createSecuritySupport(RoutingContext ctx, QuarkusHttpUser user) {
        return new SecuritySupport() {
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
    }

    private ContextSupport createContextSupport(RoutingContext ctx) {
        return new ContextSupport() {
            @Override
            public void requestContextActivated() {
                currentVertxRequest.setCurrent(ctx);
            }
        };
    }

    private void completeResponse(RoutingContext ctx, HttpMcpRequest mcpRequest, boolean containsRequest, boolean succeeded) {
        if (mcpRequest.subscriptionStream.get()) {
            return;
        }
        if (succeeded) {
            if (mcpRequest.sse.get()) {
                ctx.response().end();
            } else {
                if (!ctx.response().ended()) {
                    if (!containsRequest) {
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
    }

    void openSseStream(RoutingContext ctx, ConnectionManager connectionManager, String serverName) {
        if (serverName == null) {
            throw serverNameNotDefined();
        }
        HttpServerRequest request = ctx.request();
        // Stateless protocol does not support the GET SSE stream
        String mcpProtocolVersion = request.getHeader(MCP_PROTOCOL_VERSION_HEADER);
        if (mcpProtocolVersion != null && isStateless(mcpProtocolVersion)) {
            ctx.fail(405);
            return;
        }
        String mcpSessionId = request.getHeader(MCP_SESSION_ID_HEADER);
        if (mcpSessionId == null) {
            LOG.warnf("%s header not found", MCP_SESSION_ID_HEADER);
            ctx.fail(405);
            return;
        }
        McpConnectionBase connection = connectionManager.get(mcpSessionId);
        if (connection == null) {
            LOG.warnf("Mcp session not found: %s", mcpSessionId);
            ctx.fail(404);
            return;
        }

        HttpServerResponse response = ctx.response();
        response.setChunked(true);
        response.headers().add(HttpHeaders.CONTENT_TYPE, "text/event-stream");

        StreamableHttpMcpConnection streamableConnection = (StreamableHttpMcpConnection) connection;
        SseStream sse = new SseStream(ConnectionManager.connectionId(), response);
        streamableConnection.addSse(sse);

        // Send log notification to the client
        JsonObject log = Messages.newNotification(McpMethod.NOTIFICATIONS_MESSAGE.jsonRpcName(),
                Messages.newLog(McpLog.LogLevel.DEBUG, "SseStream",
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
        StreamableHttpMcpConnection conn = new StreamableHttpMcpConnection(id, serverConfig, serverName);
        connectionManager.add(conn);
        return conn;
    }

    private void removeAutoInitConnection(StreamableHttpMcpConnection connection) {
        if (connection.initialRequest() != null
                && connection.initialRequest().autoInitialized()
                && connectionManager.remove(connection.id())) {
            LOG.debugf("Auto-initialized session removed [%s]", connection.id());
        }
    }

    @Override
    protected InitialRequest autoInitialRequest(HttpMcpRequest mcpRequest) {
        McpHttpServerRuntimeConfig config = httpConfig.servers().get(mcpRequest.serverName());
        if (config != null && config.http().streamable().autoInit()) {
            // "If the server does not receive an MCP-Protocol-Version header, the server SHOULD assume protocol version 2025-03-26"
            // Note that this is inconsistent with initial handshake where the latest supported version is used
            McpProtocolVersion protocolVersion = McpProtocolVersion.from(mcpRequest.mcpProtocolVersion);
            if (protocolVersion == null) {
                protocolVersion = McpProtocolVersion.DEFAULT_ASSUMED;
            }

            // Try to find the clientInfo and clientCapabilities in _meta
            Implementation implementation = AUTO_INIT_IMPLEMENTATION;
            List<ClientCapability> clientCapabilities = List.of();
            JsonObject meta = findMeta(mcpRequest.message());
            if (meta != null) {
                JsonObject clientInfo = meta.getJsonObject(MetaKey.CLIENT_INFO.toString());
                if (clientInfo != null) {
                    implementation = Messages.decodeImplementation(clientInfo);
                }
                JsonObject capabilities = meta.getJsonObject(MetaKey.CLIENT_CAPABILITIES.toString());
                if (capabilities != null) {
                    List<ClientCapability> decoded = new ArrayList<>();
                    for (String name : capabilities.fieldNames()) {
                        decoded.add(new ClientCapability(name, Map.of()));
                    }
                    clientCapabilities = List.copyOf(decoded);
                }
            }

            return new InitialRequest(
                    implementation,
                    protocolVersion,
                    clientCapabilities,
                    Transport.STREAMABLE_HTTP,
                    true);
        }
        return null;
    }

    public void terminateSession(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        // Stateless protocol does not support session termination
        String mcpProtocolVersion = request.getHeader(MCP_PROTOCOL_VERSION_HEADER);
        if (mcpProtocolVersion != null && isStateless(mcpProtocolVersion)) {
            ctx.fail(405);
            return;
        }
        String mcpSessionId = request.getHeader(MCP_SESSION_ID_HEADER);
        if (mcpSessionId == null) {
            LOG.warnf("Mcp session id header is missing: %s", ctx.normalizedPath());
            ctx.fail(404);
            return;
        }
        McpConnectionBase connection = connectionManager.get(mcpSessionId);
        if (connection == null) {
            LOG.warnf("Mcp session not found: %s", mcpSessionId);
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
        if (!isStateless(mcpRequest.connection())) {
            // Add the "Mcp-Session-Id" header to the response to the "Initialize" request
            mcpRequest.response.headers().add(MCP_SESSION_ID_HEADER, mcpRequest.connection().id());
        }
    }

    private static boolean isStateless(McpConnectionBase connection) {
        InitialRequest ir = connection.initialRequest();
        return ir != null && ir.protocolVersion() != null && ir.protocolVersion().isStateless();
    }

    @Override
    protected Future<Void> onSubscriptionOpened(JsonObject message, HttpMcpRequest mcpRequest,
            Subscription subscription) {
        StreamableHttpMcpConnection connection = mcpRequest.connection();

        // Register transient connection so notification senders can find it
        if (connection.isTransient()) {
            connectionManager.add(connection);
        }

        mcpRequest.subscriptionStream.set(true);
        mcpRequest.initiateSse();

        String streamId = String.valueOf(subscription.subscriptionId());
        SseStream sse = new SseStream(streamId, mcpRequest.response, subscription);
        connection.addSse(sse);

        HttpMcpServerRecorder.setCloseHandler(mcpRequest.request, () -> {
            if (connection.removeSse(streamId)) {
                connection.removeSubscription(subscription.subscriptionId());
                LOG.debugf("Subscription stream [%s] closed [%s]", streamId, connection.id());
            }
            if (connection.isTransient() && connectionManager.remove(connection.id())) {
                LOG.debugf("Transient subscription connection removed [%s]", connection.id());
            }
        }, "subscription stream will be cleaned up");

        // Send acknowledged notification
        JsonObject params = new JsonObject().put("notifications", subscription.filter().toAcknowledgedJson());
        JsonObject acknowledged = Messages.newNotification(
                McpMethod.NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED.jsonRpcName(), params);
        McpConnectionBase.injectSubscriptionId(acknowledged, subscription.subscriptionId());

        TrafficLogging trafficLogging = config.servers().get(mcpRequest.serverName()).trafficLogging();
        if (trafficLogging.enabled()) {
            TrafficLogger.messageSent(acknowledged, connection, trafficLogging.textLimit());
        }

        LOG.debugf("Subscription stream [%s] opened [%s]", streamId, connection.id());
        return sse.sendEvent("message", acknowledged.encode());
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
    protected Future<Void> unsupportedMethod(JsonObject message, HttpMcpRequest mcpRequest) {
        if (mcpRequest.newSession) {
            connectionManager.remove(mcpRequest.connection().id());
        }
        return super.unsupportedMethod(message, mcpRequest);
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

    private static final EnumSet<McpMethod> FORCE_SSE_METHODS = EnumSet.of(
            McpMethod.TOOLS_CALL,
            McpMethod.PROMPTS_GET,
            McpMethod.RESOURCES_READ,
            McpMethod.COMPLETION_COMPLETE);

    private static final EnumSet<FeatureArgument.Provider> FORCE_SSE_PROVIDERS = EnumSet.of(
            PROGRESS,
            MCP_LOG,
            SAMPLING,
            ROOTS,
            ELICITATION);

    private boolean computeLazySseInit(String serverName, JsonObject message) {
        if (!httpConfig.servers().get(serverName).http().streamable().lazySseInit()) {
            return false;
        }
        return Messages.isRequest(message) && isForceSseMethod(message);
    }

    /**
     * Scans the request payload and returns {@code true} if it contains a JSON-RPC request.
     * <p>
     * For requests in {@link #FORCE_SSE_METHODS}:
     * <ul>
     * <li>If lazy SSE init is enabled (default), SSE is initialized lazily in {@link HttpMcpRequest#send(JsonObject)}
     * when a non-response message is actually sent.</li>
     * <li>If lazy SSE init is disabled, SSE is initialized eagerly based on the declared parameters of the feature.</li>
     * </ul>
     */
    private boolean scan(HttpMcpRequest mcpRequest) {
        JsonObject message = mcpRequest.message();
        boolean containsRequest = Messages.isRequest(message);
        if (containsRequest
                && !mcpRequest.lazySseInit
                && forceSseEager(mcpRequest, message)) {
            mcpRequest.initiateSse();
        }
        return containsRequest;
    }

    private static boolean isForceSseMethod(JsonObject message) {
        McpMethod method = McpMethod.from(message.getString("method"));
        return method != null && FORCE_SSE_METHODS.contains(method);
    }

    private boolean forceSseEager(HttpMcpRequest mcpRequest, JsonObject message) {
        McpMethod method = McpMethod.from(message.getString("method"));
        if (method != null && FORCE_SSE_METHODS.contains(method)) {
            JsonObject params = Messages.getParams(message);
            String serverName = mcpRequest.serverName();
            if (params != null) {
                return switch (method) {
                    case TOOLS_CALL -> forceSseTool(serverName, params);
                    case PROMPTS_GET -> forceSsePrompt(serverName, params);
                    case RESOURCES_READ -> forceSseResource(serverName, params);
                    case COMPLETION_COMPLETE -> forceSseCompletion(serverName, params);
                    default -> throw new IllegalArgumentException("Unexpected value: " + method);
                };
            }
        }
        return false;
    }

    private boolean forceSseTool(String serverName, JsonObject params) {
        String name = params.getString("name");
        if (name != null) {
            Boolean forceSse = toolsForceSse.get(new FeatureKey(name, serverName));
            if (forceSse != null) {
                return forceSse;
            } else {
                ToolInfo info = toolManager.getTool(name, serverName);
                return info != null && !info.isMethod() && !skipSseInit(info);
            }
        }
        return false;
    }

    private boolean forceSsePrompt(String serverName, JsonObject params) {
        String name = params.getString("name");
        if (name != null) {
            Boolean forceSse = promptsForceSse.get(new FeatureKey(name, serverName));
            if (forceSse != null) {
                return forceSse;
            } else {
                PromptInfo info = promptManager.getPrompt(name, serverName);
                return info != null && !info.isMethod() && !skipSseInit(info);
            }
        }
        return false;
    }

    private boolean forceSseResource(String serverName, JsonObject params) {
        String resourceUri = params.getString("uri");
        if (resourceUri != null) {
            Boolean forceSse = resourcesForceSse.get(new FeatureKey(resourceUri, serverName));
            if (forceSse != null) {
                return forceSse;
            }
            ResourceTemplateManager.ResourceTemplateInfo rti = resourceTemplateManager.findMatching(resourceUri, serverName);
            if (rti != null && rti.isMethod()) {
                forceSse = resourceTemplatesForceSse.get(new FeatureKey(rti.name(), serverName));
                if (forceSse != null) {
                    return forceSse;
                }
            }
            ResourceManager.ResourceInfo info = resourceManager.getResource(resourceUri, serverName);
            if (info != null) {
                return !info.isMethod() && !skipSseInit(info);
            } else {
                if (rti == null) {
                    rti = resourceTemplateManager.findMatching(resourceUri, serverName);
                }
                return rti != null && !rti.isMethod() && !skipSseInit(rti);
            }
        }
        return false;
    }

    private boolean forceSseCompletion(String serverName, JsonObject params) {
        JsonObject ref = params.getJsonObject("ref");
        if (ref != null) {
            String referenceType = ref.getString("type");
            String referenceName = ref.getString("name");
            JsonObject argument = params.getJsonObject("argument");
            String argumentName = argument != null ? argument.getString("name") : null;
            if (referenceName != null && argumentName != null) {
                if (Messages.isPromptRef(referenceType)) {
                    return forceSseCompletion(serverName, referenceName, argumentName, promptCompletionManager);
                } else if (Messages.isResourceRef(referenceType)) {
                    return forceSseCompletion(serverName, referenceName, argumentName, resourceTemplateCompletionManager);
                }
            }
        }
        return false;
    }

    private boolean forceSseCompletion(String serverName, String referenceName, String argumentName,
            CompletionManager completionManager) {
        Boolean forceSse = completionsForceSse.get(forceSseCompletionKey(referenceName, serverName, argumentName));
        if (forceSse != null) {
            return forceSse;
        } else {
            CompletionManager.CompletionInfo info = completionManager.getCompletion(referenceName, argumentName, serverName);
            return info != null && !info.isMethod() && !skipSseInit(info);
        }
    }

    private static String forceSseCompletionKey(String referenceName, String serverName, String argumentName) {
        return referenceName + "_" + serverName + "_" + argumentName;
    }

    private static boolean skipSseInit(FeatureInfo info) {
        return info.transportHints().containsKey(TransportHint.STREAMABLE_HTTP_SKIP_SSE_INIT);
    }

    static class HttpMcpRequest extends McpRequestImpl<StreamableHttpMcpConnection> implements Sender {

        final boolean newSession;

        final AtomicBoolean sse;

        final HttpServerRequest request;

        final HttpServerResponse response;

        final String mcpProtocolVersion;

        final boolean lazySseInit;

        final AtomicBoolean subscriptionStream = new AtomicBoolean(false);

        public HttpMcpRequest(String serverName, JsonObject message, StreamableHttpMcpConnection connection,
                SecuritySupport securitySupport,
                HttpServerRequest request, HttpServerResponse response, boolean newSession,
                ContextSupport contextSupport,
                CurrentIdentityAssociation currentIdentityAssociation, String mcpProtocolVersion,
                boolean lazySseInit) {
            super(serverName, message, connection, null, securitySupport, contextSupport, currentIdentityAssociation);
            this.newSession = newSession;
            this.sse = new AtomicBoolean(false);
            this.request = request;
            this.response = response;
            this.mcpProtocolVersion = mcpProtocolVersion;
            this.lazySseInit = lazySseInit;
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
            if (Messages.isResponse(message)) {
                if (sse.get()) {
                    // SSE mode - stream as event
                    // "write" is async and synchronized over http connection, and should be thread-safe
                    return response.write("event: message\ndata: " + message.encode() + "\n\n");
                } else {
                    response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
                    return response.end(message.toBuffer());
                }
            } else {
                if (sse.get()) {
                    // SSE mode already active
                    return response.write("event: message\ndata: " + message.encode() + "\n\n");
                }
                if (lazySseInit) {
                    // Lazily initialize SSE - headers are not committed until the first write
                    initiateSse();
                    return response.write("event: message\ndata: " + message.encode() + "\n\n");
                }
                // Try to use a subsidiary SSE
                LOG.debugf("Not a response - try to use a subsidiary SSE channel instead");
                return connection().send(message);
            }
        }

        @Override
        public McpProtocolVersion protocolVersion() {
            McpProtocolVersion ret = super.protocolVersion();
            return ret != null ? ret : McpProtocolVersion.from(mcpProtocolVersion);
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

    private static <F> Map<FeatureKey, Boolean> precomputeForceSse(List<FeatureMetadata<F>> features) {
        return precomputeForceSse(features, fm -> fm.info().name());
    }

    private static <F> Map<FeatureKey, Boolean> precomputeForceSse(List<FeatureMetadata<F>> features,
            Function<FeatureMetadata<F>, String> keyMapper) {
        Map<FeatureKey, Boolean> ret = new HashMap<>();
        for (FeatureMetadata<F> tm : features) {
            boolean res = false;
            for (FeatureArgument a : tm.info().arguments()) {
                if (FORCE_SSE_PROVIDERS.contains(a.provider())) {
                    res = true;
                }
            }
            for (String serverName : tm.info().serverNames()) {
                ret.put(new FeatureKey(keyMapper.apply(tm), serverName), res);
            }
        }
        return ret;
    }

    private static <F> Map<String, Boolean> precomputeCompletionForceSse(List<FeatureMetadata<F>> features) {
        Map<String, Boolean> ret = new HashMap<>();
        for (FeatureMetadata<F> tm : features) {
            boolean res = false;
            for (FeatureArgument a : tm.info().arguments()) {
                if (FORCE_SSE_PROVIDERS.contains(a.provider())) {
                    res = true;
                }
            }
            for (String serverName : tm.info().serverNames()) {
                String key = forceSseCompletionKey(tm.info().name(), serverName,
                        tm.info().arguments().stream().filter(FeatureArgument::isParam).findFirst()
                                .orElseThrow().name());
                ret.put(key, res);
            }
        }
        return ret;
    }

}
