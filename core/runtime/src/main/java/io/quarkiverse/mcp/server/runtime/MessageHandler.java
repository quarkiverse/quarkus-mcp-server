package io.quarkiverse.mcp.server.runtime;

import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.CacheScope;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpResultException;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public abstract class MessageHandler {

    private static final Logger LOG = Logger.getLogger(MessageHandler.class);

    protected Future<Void> handleFailure(Object requestId, Sender sender, McpRequest mcpRequest, Throwable cause,
            Logger logger, String errorMessage, String featureId, JsonObject responseMeta) {
        if (cause instanceof McpResultException resultException) {
            JsonObject result;
            try {
                result = resultException.result();
                if (result == null) {
                    throw new IllegalStateException(resultException.getClass().getName() + "#result() must not return null");
                }
                // The result is owned by the (possibly external) exception; sendResult() enriches the payload in place, so
                // defensively copy it only when it would actually be mutated (resultType added or response meta merged)
                if (responseMeta != null || !result.containsKey("resultType")) {
                    result = result.copy();
                }
            } catch (RuntimeException e) {
                logger.errorf(e, "Unable to obtain the result from %s [%s]", resultException.getClass().getName(), featureId);
                mcpRequest.setTracingErrorResponse(false, JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error");
                return sender.sendInternalError(requestId);
            }
            return sender.sendResult(requestId, result, responseMeta);
        } else if (cause instanceof McpException mcp) {
            mcpRequest.setTracingErrorResponse(false, mcp.getJsonRpcErrorCode(), mcp.getMessage());
            if (mcp.getData() != null) {
                return sender.send(
                        Messages.newError(requestId, mcp.getJsonRpcErrorCode(), mcp.getMessage(), mcp.getData()));
            }
            return sender.sendError(requestId, mcp.getJsonRpcErrorCode(), mcp.getMessage());
        } else if (cause instanceof Cancellation.OperationCancellationException
                || cause instanceof org.mcpjava.server.Cancellation.OperationCancelledException) {
            LOG.debugf("Operation for request %s was cancelled", requestId);
            // Skip processing
            return Future.succeededFuture();
        } else if (Failures.isSecurityFailure(cause)) {
            mcpRequest.setTracingErrorResponse(false, JsonRpcErrorCodes.SECURITY_ERROR, cause.toString());
            return sender.sendError(requestId, JsonRpcErrorCodes.SECURITY_ERROR, cause.toString());
        } else {
            logger.errorf(cause, errorMessage, featureId);
            mcpRequest.setTracingErrorResponse(false, JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error");
            return sender.sendInternalError(requestId);
        }
    }

    /**
     * Adds the {@code ttlMs} and {@code cacheScope} cache control fields to a {@code CacheableResult}.
     * <p>
     * Both fields are required as of protocol version {@code 2026-07-28}. When they are not
     * explicitly configured and the negotiated protocol version requires them ({@code stateless}), spec-valid defaults
     * are emitted: {@code ttlMs: 0} (immediately stale) and {@code cacheScope: public}. For earlier protocol versions
     * the fields are omitted unless explicitly configured.
     *
     * @param stateless whether the negotiated protocol version requires the cache control fields (i.e.
     *        {@code >= 2026-07-28})
     */
    static void putCacheControl(JsonObject result, long ttlMs, Optional<CacheScope> cacheScope, boolean stateless) {
        if (ttlMs >= 0) {
            result.put("ttlMs", ttlMs);
        } else if (stateless) {
            result.put("ttlMs", 0);
        }
        if (cacheScope.isPresent()) {
            result.put("cacheScope", cacheScope.get().getName());
        } else if (stateless) {
            result.put("cacheScope", CacheScope.PUBLIC.getName());
        }
    }
}
