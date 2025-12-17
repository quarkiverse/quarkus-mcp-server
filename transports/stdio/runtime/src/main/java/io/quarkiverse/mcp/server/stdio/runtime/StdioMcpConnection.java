package io.quarkiverse.mcp.server.stdio.runtime;

import java.io.PrintStream;
import java.util.concurrent.Callable;

import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkus.runtime.BlockingOperationControl;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class StdioMcpConnection extends McpConnectionBase {

    private final PrintStream out;

    private final Vertx vertx;

    StdioMcpConnection(String id, McpServerRuntimeConfig serverConfig, PrintStream out, Vertx vertx) {
        super(id, serverConfig);
        this.out = out;
        this.vertx = vertx;
    }

    @Override
    public Future<Void> send(JsonObject message) {
        if (message == null) {
            return Future.succeededFuture();
        }
        messageSent(message);
        if (BlockingOperationControl.isBlockingAllowed()) {
            out.println(message.encode());
            return Future.succeededFuture();
        } else {
            Promise<Void> p = Promise.promise();
            // Event loop - offload to a worker thread
            vertx.executeBlocking(new Callable<>() {

                @Override
                public Object call() throws Exception {
                    out.println(message.encode());
                    p.complete();
                    return null;
                }
            });
            return p.future();
        }
    }

}
