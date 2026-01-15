package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.McpException;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public abstract class CompletionMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(CompletionMessageHandler.class);

    private final ResponseHandlers responseHandlers;

    protected CompletionMessageHandler(ResponseHandlers responseHandlers) {
        this.responseHandlers = responseHandlers;
    }

    protected abstract Future<CompletionResponse> execute(String key, JsonObject message, ArgumentProviders argProviders,
            McpRequest mcpRequest) throws McpException;

    protected String referenceName(JsonObject ref) {
        return ref.getString("name");
    }

    Future<Void> complete(JsonObject message, Object id, JsonObject ref, JsonObject argument, Sender sender,
            McpRequest mcpRequest) {
        String referenceName = referenceName(ref);
        String argumentName = argument.getString("name");
        LOG.debugf("Complete %s for argument %s [id: %s]", referenceName, argumentName, id);

        String key = referenceName + "_" + argumentName;

        ArgumentProviders argProviders = new ArgumentProviders(message,
                Map.of(argumentName, argument.getString("value")), mcpRequest.connection(), id, null, sender,
                Messages.getProgressToken(message), responseHandlers, mcpRequest.serverName());

        try {
            Future<CompletionResponse> fu = execute(key, message, argProviders, mcpRequest);
            return fu.compose(completionResponse -> {
                JsonObject result = new JsonObject();
                JsonObject completion = new JsonObject()
                        .put("values", completionResponse.values());
                if (completionResponse.total() != null) {
                    completion.put("total", completionResponse.total());
                }
                if (completionResponse.hasMore() != null) {
                    completion.put("hasMore", completionResponse.hasMore());
                }
                result.put("completion", completion);
                return sender.sendResult(id, result);
            }, cause -> handleFailure(id, sender, mcpRequest.connection(), cause, LOG, "Unable to complete %s", referenceName));
        } catch (McpException e) {
            return sender.sendError(id, e.getJsonRpcErrorCode(), e.getMessage());
        }
    }

}
