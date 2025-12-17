package io.quarkiverse.mcp.server.http.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServersBuildTimeConfig;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class HttpMcpServerRecorder {

    private static final Logger LOG = Logger.getLogger(HttpMcpServerRecorder.class);

    static final String CONTEXT_KEY = "mcp.http.server-name";

    private final RuntimeValue<McpServersRuntimeConfig> config;

    private final McpHttpServersBuildTimeConfig sseConfig;

    public HttpMcpServerRecorder(RuntimeValue<McpServersRuntimeConfig> config, McpHttpServersBuildTimeConfig sseConfig) {
        this.config = config;
        this.sseConfig = sseConfig;
    }

    public Handler<RoutingContext> createMcpEndpointHandler(String serverName) {
        ArcContainer container = Arc.container();
        ConnectionManager connectionManager = container.instance(ConnectionManager.class).get();
        StreamableHttpMcpMessageHandler handler = container.instance(StreamableHttpMcpMessageHandler.class).get();
        return new Handler<RoutingContext>() {

            @Override
            public void handle(RoutingContext ctx) {
                ctx.put(CONTEXT_KEY, serverName);
                HttpMethod method = ctx.request().method();
                if (HttpMethod.GET.equals(method)) {
                    handler.openSseStream(ctx, connectionManager, serverName);
                } else if (HttpMethod.POST.equals(method)) {
                    handler.handle(ctx);
                } else if (HttpMethod.DELETE.equals(method)) {
                    handler.terminateSession(ctx);
                } else {
                    throw new IllegalArgumentException("Unexpected HTTP method: " + method);
                }
            }
        };
    }

    public Handler<RoutingContext> createSseEndpointHandler(String mcpPath, String serverName) {

        McpServerRuntimeConfig serverConfig = config.getValue().servers().get(serverName);

        ArcContainer container = Arc.container();
        ConnectionManager connectionManager = container.instance(ConnectionManager.class).get();

        return new Handler<RoutingContext>() {

            @Override
            public void handle(RoutingContext ctx) {
                // The client may attempt to POST an initialize request to the SSE endpoint
                // to test whether the Streamable transport is supported
                // (for the case when the server combines the legacy SSE endpoint and the new MCP streamable endpoint)
                // This is not our case but we should still return 405
                if (HttpMethod.POST.equals(ctx.request().method())) {
                    ctx.fail(405);
                    return;
                }

                ctx.put(CONTEXT_KEY, serverName);
                HttpServerResponse response = ctx.response();
                response.setChunked(true);
                response.headers().add(HttpHeaders.CONTENT_TYPE, "text/event-stream");

                String id = ConnectionManager.connectionId();
                LOG.debugf("SSE connection initialized [%s]", id);

                SseMcpConnection connection = new SseMcpConnection(id, serverConfig, response);
                connectionManager.add(connection);

                // TODO we cannot override the close handler set/used by Quarkus HTTP
                setCloseHandler(ctx.request(), id, connectionManager);

                // By default /mcp/messages/{generatedId}
                StringBuilder endpointPath = new StringBuilder(mcpPath);
                if (!mcpPath.endsWith("/")) {
                    endpointPath.append("/");
                }
                endpointPath.append("messages/").append(id);
                if (sseConfig.servers().get(serverName).http().messageEndpoint().includeQueryParams()) {
                    // Do not use HttpServerRequest#params() as it also contains path params
                    MultiMap queryParams = ctx.queryParams();
                    if (!queryParams.isEmpty()) {
                        endpointPath.append("?");
                        for (Iterator<Entry<String, String>> it = queryParams.iterator(); it.hasNext();) {
                            var e = it.next();
                            endpointPath
                                    .append(e.getKey())
                                    .append("=")
                                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                            if (it.hasNext()) {
                                endpointPath.append("&");
                            }
                        }
                    }
                }

                String endpoint = endpointPath.toString();
                LOG.debugf("POST endpoint path: %s", endpoint);
                connection.sendEvent("endpoint", endpoint);
            }
        };
    }

    static void setCloseHandler(HttpServerRequest request, String connectionId, ConnectionManager connectionManager) {
        setCloseHandler(request, () -> {
            if (connectionManager.remove(connectionId)) {
                LOG.debugf("Connection %s closed", connectionId);
            }
            // Connection may have been removed earlier...
        }, "client should close the connection [%s] explicitly".formatted(connectionId));
    }

    static void setCloseHandler(HttpServerRequest request, Runnable closeAction, String errorMessage) {
        HttpConnection connection = request.connection();
        if (connection instanceof ConnectionBase base) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ConnectionBase.class, MethodHandles.lookup());
                VarHandle varHandle = lookup.findVarHandle(ConnectionBase.class, "closeHandler", Handler.class);
                Handler<Void> closeHandler = (Handler<Void>) varHandle.get(base);
                base.closeHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        if (closeHandler != null) {
                            closeHandler.handle(event);
                        }
                        closeAction.run();
                    }
                });
            } catch (Exception e) {
                LOG.warnf(e, "Unable to set close handler - %s", errorMessage);
            }
        } else {
            LOG.warnf("Unable to set close handler - %s", errorMessage);
        }
    }

    public Consumer<Route> addBodyHandler(Handler<RoutingContext> bodyHandler) {
        return new Consumer<Route>() {

            @Override
            public void accept(Route route) {
                route.handler(bodyHandler);
            }
        };
    }

    public Handler<RoutingContext> createMessagesEndpointHandler(String serverName) {
        SseMcpMessageHandler handler = Arc.container().instance(SseMcpMessageHandler.class).get();
        return new Handler<RoutingContext>() {

            @Override
            public void handle(RoutingContext ctx) {
                ctx.put(CONTEXT_KEY, serverName);
                handler.handle(ctx);
            }
        };
    }

    public Function<SyntheticCreationalContext<McpServerEndpoints>, McpServerEndpoints> createMcpServerEndpoints(
            List<McpServerEndpoints.McpServerEndpoint> endpoints) {
        return new Function<SyntheticCreationalContext<McpServerEndpoints>, McpServerEndpoints>() {

            @Override
            public McpServerEndpoints apply(SyntheticCreationalContext<McpServerEndpoints> t) {
                return new McpServerEndpoints(endpoints);
            }
        };
    }

}
