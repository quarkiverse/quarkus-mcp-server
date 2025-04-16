package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.Root;
import io.quarkiverse.mcp.server.Roots;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RootsImpl implements Roots {

    static RootsImpl from(ArgumentProviders argProviders) {
        return new RootsImpl(argProviders.connection().initialRequest(), argProviders.sender(),
                argProviders.responseHandlers());
    }

    private final InitialRequest initialRequest;

    private final Sender sender;

    private final ResponseHandlers responseHandlers;

    RootsImpl(InitialRequest initialRequest, Sender sender, ResponseHandlers responseHandlers) {
        this.initialRequest = initialRequest;
        this.sender = sender;
        this.responseHandlers = responseHandlers;
    }

    @Override
    public boolean isSupported() {
        return initialRequest.supportsRoots();
    }

    @Override
    public Uni<List<Root>> list() {
        if (!initialRequest.supportsRoots()) {
            throw new IllegalStateException(
                    "Client " + initialRequest.implementation() + " does not support the `roots` capability");
        }
        // Send a "roots/list" message to the client and register a consumer
        // that will be called when a response arrives
        return Uni.createFrom().completionStage(() -> {
            CompletableFuture<List<Root>> ret = new CompletableFuture<List<Root>>();
            Long id = responseHandlers.newRequest(m -> {
                JsonObject result = m.getJsonObject("result");
                JsonArray roots = result.getJsonArray("roots");
                List<Root> list = new ArrayList<>(roots.size());
                for (Object root : roots) {
                    if (root instanceof JsonObject jo) {
                        list.add(new Root(jo.getString("name"), jo.getString("uri")));
                    }
                }
                ret.complete(list);
            });
            sender.send(Messages.newRequest(id, McpMessageHandler.ROOTS_LIST));
            return ret;
        });
    }

}
