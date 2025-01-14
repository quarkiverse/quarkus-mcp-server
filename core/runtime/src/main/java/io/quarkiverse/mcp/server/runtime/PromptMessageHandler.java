package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.PromptResponse;
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
        responder.sendResult(id, new JsonObject().put("prompts", prompts));
    }

    void promptsGet(JsonObject message, Responder responder, McpConnection connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String promptName = params.getString("name");
        LOG.infof("Get prompt %s [id: %s]", promptName, id);

        ArgumentProviders argProviders = new ArgumentProviders(params.getJsonObject("arguments").getMap(), connection, id);

        try {
            Future<PromptResponse> fu = promptManager.execute(promptName, argProviders);
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
                        LOG.errorf(ar.cause(), "Unable to obtain prompt %s", promptName);
                        responder.sendInternalError(id);
                    }
                }
            });
        } catch (McpException e) {
            responder.sendError(id, e.getJsonRpcError(), e.getMessage());
        }

    }

}
