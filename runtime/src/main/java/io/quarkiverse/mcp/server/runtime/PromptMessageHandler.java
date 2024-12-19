package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.McpMessagesHandler.newResult;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.runtime.McpMessagesHandler.Responder;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class PromptMessageHandler {

    private static final Logger LOG = Logger.getLogger(PromptMessageHandler.class);

    private final PromptManager promptManager;

    PromptMessageHandler(PromptManager promptManager) {
        this.promptManager = promptManager;
    }

    void promptsList(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        LOG.infof("List prompts [id: %s]", id);
        JsonArray prompts = new JsonArray();
        for (FeatureMetadata<PromptResponse> resource : promptManager.list()) {
            prompts.add(resource.asJson());
        }
        responder.ok(newResult(id, new JsonObject()
                .put("prompts", prompts)));
    }

    void promptsGet(JsonObject message, Responder responder, McpConnectionImpl connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String promptName = params.getString("name");
        LOG.infof("Get prompt %s [id: %s]", promptName, id);

        ArgumentProviders argProviders = new ArgumentProviders(params.getJsonObject("arguments").getMap(), connection, id);

        Future<PromptResponse> fu = promptManager.get(promptName, argProviders);
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
                    responder.ok(newResult(id, result));
                } else {
                    responder.badRequest(ar.cause(), "Unable to obtain prompt %s", promptName);
                }
            }
        });
    }

}
