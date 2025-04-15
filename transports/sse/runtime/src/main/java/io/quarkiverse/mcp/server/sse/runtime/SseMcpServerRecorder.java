package io.quarkiverse.mcp.server.sse.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.TrafficLogger;
import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class SseMcpServerRecorder {

    private static final Logger LOG = Logger.getLogger(SseMcpServerRecorder.class);

    private final McpRuntimeConfig config;

    public SseMcpServerRecorder(McpRuntimeConfig config) {
        this.config = config;
    }

    public Handler<RoutingContext> createSseEndpointHandler(String mcpPath) {

        ArcContainer container = Arc.container();
        ConnectionManager connectionManager = container.instance(ConnectionManager.class).get();
        TrafficLogger trafficLogger = config.trafficLogging().enabled() ? new TrafficLogger(config.trafficLogging().textLimit())
                : null;

        return new Handler<RoutingContext>() {

            @Override
            public void handle(RoutingContext ctx) {
                HttpServerResponse response = ctx.response();
                response.setChunked(true);
                response.headers().add(HttpHeaders.CONTENT_TYPE, "text/event-stream");

                String id = Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes());

                LOG.debugf("Connection initialized [%s]", id);

                SseMcpConnection connection = new SseMcpConnection(id, config.clientLogging().defaultLevel(), trafficLogger,
                        config.autoPingInterval(), response);
                connectionManager.add(connection);

                // TODO we cannot override the close handler set/used by Quarkus HTTP
                setCloseHandler(ctx.request(), id, connectionManager);

                // By default /mcp/messages/{generatedId}
                String endpointPath = mcpPath.endsWith("/") ? (mcpPath + "messages/" + id) : (mcpPath + "/messages/" + id);
                LOG.debugf("POST endpoint path: %s", endpointPath);

                connection.sendEvent("endpoint", endpointPath);
            }
        };
    }

    private void setCloseHandler(HttpServerRequest request, String connectionId, ConnectionManager connectionManager) {
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
                        if (connectionManager.remove(connectionId)) {
                            LOG.debugf("Connection %s closed", connectionId);
                        }
                        // Connection may have been removed earlier...
                    }
                });
            } catch (Exception e) {
                LOG.warnf(e, "Unable to set close handler - client should close the connection [%s] explicitly", connectionId);
            }
        } else {
            LOG.warnf("Unable to set close handler - client should close the connection [%s] explicitly", connectionId);
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

    public Handler<RoutingContext> createMessagesEndpointHandler() {
        return Arc.container().instance(SseMcpMessageHandler.class).get();
    }

}
