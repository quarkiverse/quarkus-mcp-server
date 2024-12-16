package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.McpMessagesHandler.newResult;

import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkus.arc.Arc;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class PromptMessageHandler {

    private static final Logger LOG = Logger.getLogger(PromptMessageHandler.class);

    void promptsList(JsonObject message, RoutingContext ctx) {
        Object id = message.getValue("id");
        LOG.infof("List prompts [id: %s]", id);
        PromptManager promptManager = Arc.container().instance(PromptManager.class).get();
        List<FeatureMethodInfo> prompts = promptManager.list();
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        ctx.end(newResult(id, new JsonObject()
                .put("prompts", new JsonArray(prompts))).encode());
    }

    void promptsGet(JsonObject message, RoutingContext ctx, McpConnectionImpl connection) {
        Object id = message.getValue("id");
        JsonObject params = message.getJsonObject("params");
        String promptName = params.getString("name");
        LOG.infof("Get prompt %s [id: %s]", promptName, id);

        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        ArgumentProviders argProviders = new ArgumentProviders(params.getJsonObject("arguments").getMap(), connection, id);

        PromptManager promptManager = Arc.container().instance(PromptManager.class).get();
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
                    ctx.end(newResult(id, result).encode());
                } else {
                    LOG.error("Unable to obtain prompt", ar.cause());
                    ctx.fail(500);
                }
            }
        });
    }

}