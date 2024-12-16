package io.quarkiverse.mcp.server.runtime;

import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class McpServerRecorder {

    private static final Logger LOG = Logger.getLogger(McpServerRecorder.class);

    private final McpBuildTimeConfig config;

    public McpServerRecorder(McpBuildTimeConfig config) {
        this.config = config;
    }

    public Handler<RoutingContext> createSseEndpointHandler(String mcpPath) {

        ArcContainer container = Arc.container();
        ConnectionManager connectionManager = container.instance(ConnectionManager.class).get();

        return new Handler<RoutingContext>() {

            @Override
            public void handle(RoutingContext ctx) {
                String contentType = ctx.request().getHeader(HttpHeaders.CONTENT_TYPE);
                if (!"text/event-stream".equals(contentType)) {
                    LOG.errorf("Invalid content type: %s", contentType);
                    ctx.fail(400);
                    return;
                }

                HttpServerResponse response = ctx.response();
                response.setChunked(true);
                response.headers().add(HttpHeaders.TRANSFER_ENCODING, "chunked");
                response.headers().add(HttpHeaders.CONTENT_TYPE, "text/event-stream");

                String id = Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes());

                LOG.infof("Client connection initialized [%s]", id);

                McpConnectionImpl connection = new McpConnectionImpl(id, response);
                connectionManager.add(connection);

                // /mcp/messages?id=generatedId
                String endpointPath = mcpPath + "/messages?id=" + id;

                // https://spec.modelcontextprotocol.io/specification/basic/transports/#http-with-sse
                connection.sendEvent("endpoint", endpointPath);
            }
        };
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
        return new McpMessagesHandler(Arc.container().instance(ConnectionManager.class).get(), config);
    }

}
