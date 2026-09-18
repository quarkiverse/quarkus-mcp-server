package io.quarkiverse.mcp.server.runtime;

import static io.quarkiverse.mcp.server.runtime.Messages.getParams;

import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.runtime.FeatureManagerBase.FeatureExecutionContext;
import io.quarkiverse.mcp.server.runtime.ToolCallInterceptor.ToolCall;
import io.quarkiverse.mcp.server.runtime.config.McpServerRuntimeConfig;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ToolMessageHandler extends MessageHandler {

    private static final Logger LOG = Logger.getLogger(ToolMessageHandler.class);

    private final ToolManagerImpl manager;

    private final McpServersRuntimeConfig config;

    ToolMessageHandler(ToolManagerImpl manager, McpServersRuntimeConfig config) {
        this.manager = Objects.requireNonNull(manager);
        this.config = config;
    }

    Future<Void> toolsList(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta) {
        Object id = Messages.getId(message);
        Cursor cursor = Messages.getCursor(message, mcpRequest.sender());
        if (cursor == null) {
            return Future.succeededFuture();
        }
        LOG.debugf("List tools [id: %s, cursor: %s]", id, cursor);

        McpServerRuntimeConfig serverConfig = config.servers().get(mcpRequest.serverName());
        if (serverConfig == null) {
            throw new IllegalStateException("Server config not found: " + mcpRequest.serverName());
        }
        int pageSize = serverConfig.tools().pageSize();

        JsonArray tools = new JsonArray();
        JsonObject result = new JsonObject().put("tools", tools);
        Page<ToolManager.ToolInfo> page = manager.fetchPage(mcpRequest, cursor, pageSize, message);
        for (ToolManager.ToolInfo info : page) {
            try {
                tools.add(info.asJson());
            } catch (McpException e) {
                return mcpRequest.sender().sendError(id, e.getJsonRpcErrorCode(), e.getMessage());
            } catch (Exception e) {
                LOG.errorf(e, "Unable to encode Tool [%s] as JSON", info.name());
                return mcpRequest.sender().sendInternalError(id);
            }
        }
        if (page.hasNextCursor()) {
            ToolManager.ToolInfo last = page.lastInfo();
            result.put("nextCursor", Cursor.encode(last.createdAt(), cursor.snapshotTimestamp()));
        }
        putCacheControl(result, serverConfig.tools().ttlMs(), serverConfig.tools().cacheScope(),
                mcpRequest.protocolVersion().isStateless());
        return mcpRequest.sender().sendResult(id, result, responseMeta);
    }

    Future<Void> toolsCall(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta) {
        Object id = Messages.getId(message);
        JsonObject params = getParams(message);
        if (params == null) {
            return mcpRequest.sender()
                    .sendError(id, JsonRpcErrorCodes.INVALID_REQUEST, "Missing required params");
        }
        String toolName = params.getString("name");
        LOG.debugf("Call tool %s [id: %s]", toolName, id);

        if (toolName != null && !manager.interceptors.isEmpty()) {
            // Interceptors are only consulted for tools that exist and pass the filters
            ToolInfo tool = manager.getTool(toolName, mcpRequest.serverName());
            if (tool != null && manager.isAvailable(tool, mcpRequest, message)) {
                ToolCall toolCall = new ToolCallImpl(tool, message, mcpRequest, responseMeta);
                for (ToolCallInterceptor interceptor : manager.interceptors) {
                    Future<Void> ret = interceptor.intercept(toolCall);
                    if (ret != null) {
                        return ret;
                    }
                }
            }
        }
        return toolsCall(message, mcpRequest, responseMeta, toolName, null);
    }

    private Future<Void> toolsCall(JsonObject message, McpRequest mcpRequest, JsonObject responseMeta, String toolName,
            Map<Class<?>, Object> customProviders) {
        Object id = Messages.getId(message);
        try {
            Future<ToolResponse> fu = manager.execute(toolName,
                    new FeatureExecutionContext(message, mcpRequest, null, customProviders));
            return fu.compose(toolResponse -> {
                if (toolResponse.isError()) {
                    mcpRequest.setTracingErrorResponse(true, null, null);
                }
                return mcpRequest.sender().sendResult(id, JsonObject.mapFrom(toolResponse), responseMeta);
            },
                    cause -> handleFailure(id, mcpRequest.sender(), mcpRequest, cause, LOG,
                            "Unable to call tool %s", toolName, responseMeta));
        } catch (McpException e) {
            return mcpRequest.sender().sendError(id, e.getJsonRpcErrorCode(), e.getMessage());
        }
    }

    private final class ToolCallImpl implements ToolCall {

        private final ToolInfo tool;
        private final JsonObject message;
        private final McpRequest mcpRequest;
        private final JsonObject responseMeta;

        ToolCallImpl(ToolInfo tool, JsonObject message, McpRequest mcpRequest, JsonObject responseMeta) {
            this.tool = tool;
            this.message = message;
            this.mcpRequest = mcpRequest;
            this.responseMeta = responseMeta;
        }

        @Override
        public ToolInfo tool() {
            return tool;
        }

        @Override
        public McpRequest mcpRequest() {
            return mcpRequest;
        }

        @Override
        public JsonObject message() {
            return message;
        }

        @Override
        public Object requestId() {
            return Messages.getId(message);
        }

        @Override
        public JsonObject responseMeta() {
            return responseMeta;
        }

        @Override
        public Future<Void> proceed(Map<Class<?>, Object> customProviders) {
            return toolsCall(message, mcpRequest, responseMeta, tool.name(), customProviders);
        }

        @Override
        public Future<ToolResponse> executeDetached(Map<Class<?>, Object> customProviders) {
            Promise<ToolResponse> ret = Promise.promise();
            // Create a new duplicated context and execute the tool on this context
            Context context = VertxContext.createNewDuplicatedContext(manager.vertx.getOrCreateContext());
            VertxContextSafetyToggle.setContextSafe(context, true);
            context.runOnContext(v -> {
                mcpRequest.contextStart();
                Future<ToolResponse> fu;
                try {
                    fu = manager.execute(tool.name(),
                            new FeatureExecutionContext(message, mcpRequest, null, customProviders));
                } catch (Throwable e) {
                    fu = Future.failedFuture(e);
                }
                fu.onComplete(r -> {
                    try {
                        mcpRequest.contextEnd(r.cause());
                    } finally {
                        if (r.succeeded()) {
                            ret.complete(r.result());
                        } else {
                            ret.fail(r.cause());
                        }
                    }
                });
            });
            return ret.future();
        }

        @Override
        public JsonObject failureResponse(Throwable cause) {
            return MessageHandler.failureResponse(requestId(), mcpRequest, cause, LOG, "Unable to call tool %s",
                    tool.name());
        }

    }

}
