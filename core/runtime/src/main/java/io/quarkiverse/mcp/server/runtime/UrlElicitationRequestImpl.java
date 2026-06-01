package io.quarkiverse.mcp.server.runtime;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.ElicitationResponse.Action;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.UrlElicitationRequest;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class UrlElicitationRequestImpl implements UrlElicitationRequest {

    private static final Logger LOG = Logger.getLogger(UrlElicitationRequestImpl.class);

    private final String message;
    private final String url;
    private final String elicitationId;

    private final Sender sender;
    private final ServerRequests serverRequests;
    private final Duration timeout;
    private final Duration completionTimeout;
    private final McpTracing mcpTracing;
    private final String connectionId;

    UrlElicitationRequestImpl(String message, String url, String elicitationId, Sender sender,
            ServerRequests serverRequests, Duration timeout, Duration completionTimeout,
            McpTracing mcpTracing, String connectionId) {
        this.message = message;
        this.url = url;
        this.elicitationId = elicitationId;
        this.sender = sender;
        this.serverRequests = serverRequests;
        this.timeout = timeout;
        this.completionTimeout = completionTimeout;
        this.mcpTracing = mcpTracing;
        this.connectionId = connectionId;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public String url() {
        return url;
    }

    @Override
    public String elicitationId() {
        return elicitationId;
    }

    @Override
    public Uni<ElicitationResponse> send() {
        AtomicLong id = new AtomicLong();
        Uni<ElicitationResponse> ret = Uni.createFrom().completionStage(() -> {
            CompletableFuture<ElicitationResponse> future = new CompletableFuture<>();
            Long requestId = serverRequests.newRequest(m -> {
                JsonObject result = m.getJsonObject("result");
                if (result == null) {
                    throw new IllegalStateException("Invalid elicitation response: " + m);
                }
                Action action = Action.valueOf(result.getString("action").toUpperCase());
                future.complete(new ElicitationResponse(action, new ElicitationRequestImpl.ContentImpl(new JsonObject()),
                        MetaImpl.from(result)));
            });
            id.set(requestId);
            serverRequests.registerElicitation(elicitationId, connectionId, completionTimeout);
            JsonObject params = new JsonObject()
                    .put("mode", "url")
                    .put("message", message)
                    .put("url", url)
                    .put("elicitationId", elicitationId);
            if (mcpTracing != null) {
                JsonObject meta = params.getJsonObject("_meta");
                if (meta == null) {
                    meta = new JsonObject();
                    params.put("_meta", meta);
                }
                mcpTracing.injectMcpOtelContext(meta);
            }
            sender.send(Messages.newRequest(requestId, McpMethod.ELICITATION_CREATE.jsonRpcName(), params));
            return future;
        });
        if (!timeout.isNegative() && !timeout.isZero()) {
            ret = ret.ifNoItem()
                    .after(timeout).fail()
                    .onFailure(TimeoutException.class).invoke(te -> {
                        long requestId = id.get();
                        if (requestId != 0 && serverRequests.removeResponseHandler(requestId)) {
                            LOG.debugf("Response handler for %s removed due to timeout", requestId);
                        }
                        serverRequests.removeElicitation(elicitationId);
                    });
        }
        return ret;
    }

}
