package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class PromptMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(PromptMessageHandler.class);

    private final PromptManagerImpl manager;

    private final int pageSize;

    PromptMessageHandler(PromptManagerImpl manager, int pageSize) {
        this.manager = Objects.requireNonNull(manager);
        this.pageSize = pageSize;
    }

    void promptsList(JsonObject message, Responder responder) {
        Object id = message.getValue("id");
        Cursor cursor = Messages.getCursor(message, responder);

        LOG.debugf("List prompts [id: %s, cursor: %s]", id, cursor);

        JsonArray prompts = new JsonArray();
        JsonObject result = new JsonObject().put("prompts", prompts);
        Page<PromptManager.PromptInfo> page = manager.fetchPage(cursor, pageSize);
        for (PromptManager.PromptInfo info : page) {
            prompts.add(info.asJson());
        }
        if (page.hasNextCursor()) {
            PromptManager.PromptInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), last.name()));
        }
        responder.sendResult(id, result);
    }

    void promptsGet(JsonObject message, Responder responder, McpConnection connection, SecuritySupport securitySupport) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String promptName = params.getString("name");
        LOG.debugf("Get prompt %s [id: %s]", promptName, id);

        Map<String, Object> args = params.containsKey("arguments") ? params.getJsonObject("arguments").getMap() : Map.of();
        ArgumentProviders argProviders = new ArgumentProviders(args, connection, id, null, responder);

        try {
            Future<PromptResponse> fu = manager.execute(promptName, new FeatureExecutionContext(argProviders, securitySupport));
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
                        handleFailure(id, responder, connection, ar.cause(), LOG, "Unable to obtain prompt %s", promptName);
                    }
                }
            });
        } catch (McpException e) {
            responder.sendError(id, e.getJsonRpcError(), e.getMessage());
        }

    }

}
