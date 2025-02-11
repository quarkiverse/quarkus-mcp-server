package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptResponse;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class PromptMessageHandler {

    private static final Logger LOG = Logger.getLogger(PromptMessageHandler.class);

    private final PromptManagerImpl manager;

    PromptMessageHandler(PromptManagerImpl manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    void promptsList(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        LOG.debugf("List prompts [id: %s]", id);
        JsonArray prompts = new JsonArray();
        for (PromptManager.PromptInfo prompt : manager) {
            prompts.add(prompt.asJson());
        }
        responder.sendResult(id, new JsonObject().put("prompts", prompts));
    }

    void promptsGet(JsonObject message, Responder responder, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String promptName = params.getString("name");
        LOG.debugf("Get prompt %s [id: %s]", promptName, id);

        Map<String, Object> args = params.containsKey("arguments") ? params.getJsonObject("arguments").getMap() : Map.of();
        ArgumentProviders argProviders = new ArgumentProviders(args, connection, id, null, responder);

        try {
            Future<PromptResponse> fu = manager.execute(promptName, argProviders);
            fu.onComplete(new Handler<AsyncResult<PromptResponse>>() {
                @Override
                public void handle(AsyncResult<PromptResponse> ar) {
                    if (ar.succeeded()) {
                        PromptResponse promptResponse = ar.result();
                        JsonObject result = new JsonObject();
                        if (promptResponse.description() != null) {
                            result.put("description", promptResponse.description());
                        }
                        result.put("messages", promptResponse.messages());
                        responder.sendResult(id, result);
                    } else {
                        Throwable cause = ar.cause();
                        if (cause instanceof McpException mcp) {
                            responder.sendError(id, mcp.getJsonRpcError(), mcp.getMessage());
                        } else {
                            LOG.errorf(ar.cause(), "Unable to obtain prompt %s", promptName);
                            responder.sendInternalError(id);
                        }
                    }
                }
            });
        } catch (McpException e) {
            responder.sendError(id, e.getJsonRpcError(), e.getMessage());
        }

    }

}
