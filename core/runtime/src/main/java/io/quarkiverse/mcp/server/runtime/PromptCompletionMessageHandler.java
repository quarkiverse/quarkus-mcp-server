package io.quarkiverse.mcp.server.runtime;

import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.McpConnection;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

class PromptCompletionMessageHandler {

    private static final Logger LOG = Logger.getLogger(PromptCompletionMessageHandler.class);

    private final PromptCompleteManager manager;

    PromptCompletionMessageHandler(PromptCompleteManager manager) {
        this.manager = manager;
    }

    void promptComplete(Object id, JsonObject ref, JsonObject argument, Responder responder, McpConnection connection) {
        String promptName = ref.getString("name");
        String argumentName = argument.getString("name");

        LOG.infof("Complete prompt %s for argument %s [id: %s]", promptName, argumentName, id);

        String key = promptName + "_" + argumentName;

        ArgumentProviders argProviders = new ArgumentProviders(
                Map.of(argumentName, argument.getString("value")), connection, id);

        try {
            Future<CompletionResponse> fu = manager.execute(key, argProviders);
            fu.onComplete(new Handler<AsyncResult<CompletionResponse>>() {
                @Override
                public void handle(AsyncResult<CompletionResponse> ar) {
                    if (ar.succeeded()) {
                        CompletionResponse completionResponse = ar.result();
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
                        responder.sendResult(id, result);
                    } else {
                        LOG.errorf(ar.cause(), "Unable to complete prompt %s", promptName);
                        responder.sendInternalError(id);
                    }
                }
            });
        } catch (McpException e) {
            responder.sendError(id, e.getJsonRpcError(), e.getMessage());
        }
    }

}
