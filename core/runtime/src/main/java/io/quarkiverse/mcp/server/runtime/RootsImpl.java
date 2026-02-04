package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.Root;
import io.quarkiverse.mcp.server.Roots;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RootsImpl implements Roots {

    private static final Logger LOG = Logger.getLogger(Roots.class);

    static RootsImpl from(ArgumentProviders argProviders) {
        return new RootsImpl(argProviders.connection(), argProviders.sender(),
                argProviders.responseHandlers(), argProviders.responseHandlers().getRootsTimeout(argProviders.serverName()));
    }

    private final McpConnection connection;

    private final Sender sender;

    private final ResponseHandlers responseHandlers;

    private final Duration timeout;

    RootsImpl(McpConnection connection, Sender sender, ResponseHandlers responseHandlers, Duration timeout) {
        this.connection = connection;
        this.sender = sender;
        this.responseHandlers = responseHandlers;
        this.timeout = timeout;
    }

    @Override
    public boolean isSupported() {
        return connection.initialRequest().supportsRoots();
    }

    @Override
    public Uni<List<Root>> list() {
        if (!connection.status().isClientInitialized()) {
            throw McpMessageHandler.clientNotInitialized(connection);
        }
        if (!isSupported()) {
            throw new IllegalStateException(
                    "Client " + connection.initialRequest().implementation() + " does not support the `roots` capability");
        }
        // Send a "roots/list" message to the client and register a handler
        // that will be called when a response arrives
        AtomicLong id = new AtomicLong();
        Uni<List<Root>> ret = Uni.createFrom().completionStage(() -> {
            CompletableFuture<List<Root>> future = new CompletableFuture<List<Root>>();
            Long requestId = responseHandlers.newRequest(m -> {
                JsonObject result = m.getJsonObject("result");
                JsonArray roots = result.getJsonArray("roots");
                List<Root> list = new ArrayList<>(roots.size());
                for (Object root : roots) {
                    if (root instanceof JsonObject jo) {
                        list.add(new Root(jo.getString("name"), jo.getString("uri")));
                    }
                }
                future.complete(list);
            });
            id.set(requestId);
            sender.send(Messages.newRequest(requestId, McpMethod.ROOTS_LIST.jsonRpcName()));
            return future;
        });
        if (!timeout.isNegative() && !timeout.isZero()) {
            ret = ret.ifNoItem()
                    .after(timeout).fail()
                    .onFailure(TimeoutException.class).invoke(te -> {
                        long requestId = id.get();
                        if (requestId != 0 && responseHandlers.remove(requestId)) {
                            LOG.debugf("Response handler for %s removed due to timeout", requestId);
                        }
                    });
        }
        return ret;
    }

}
