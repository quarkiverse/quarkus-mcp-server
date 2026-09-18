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
        JsonObject response = failureResponse(requestId, mcpRequest, cause, logger, errorMessage, featureId);
        if (response == null) {
            // The operation was cancelled - skip processing
            return Future.succeededFuture();
        }
        JsonObject result = response.getJsonObject("result");
        if (result != null) {
            return sender.sendResult(requestId, result, responseMeta);
        }
        return sender.send(response);
    }

    /**
     * Converts an execution failure into a JSON-RPC response message.
     *
     * @return the JSON-RPC response message with either the {@code result} (for an {@link McpResultException}) or the
     *         {@code error}, or {@code null} if the operation was cancelled
     */
    static JsonObject failureResponse(Object requestId, McpRequest mcpRequest, Throwable cause,
            Logger logger, String errorMessage, String featureId) {
        if (cause instanceof McpResultException resultException) {
            JsonObject result;
            try {
                result = resultException.result();
                if (result == null) {
                    throw new IllegalStateException(resultException.getClass().getName() + "#result() must not return null");
                }
                // The result is owned by the (possibly external) exception and the payload may be enriched in place later,
                // so defensively copy it
                result = result.copy();
            } catch (RuntimeException e) {
                logger.errorf(e, "Unable to obtain the result from %s [%s]", resultException.getClass().getName(), featureId);
                mcpRequest.setTracingErrorResponse(false, JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error");
                return Messages.newError(requestId, JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error");
            }
            return Messages.newResult(requestId, result);
        } else if (cause instanceof McpException mcp) {
            mcpRequest.setTracingErrorResponse(false, mcp.getJsonRpcErrorCode(), mcp.getMessage());
            return Messages.newError(requestId, mcp.getJsonRpcErrorCode(), mcp.getMessage(), mcp.getData());
        } else if (cause instanceof Cancellation.OperationCancellationException
                || cause instanceof org.mcpjava.server.Cancellation.OperationCancelledException) {
            LOG.debugf("Operation for request %s was cancelled", requestId);
            return null;
        } else if (Failures.isSecurityFailure(cause)) {
            mcpRequest.setTracingErrorResponse(false, JsonRpcErrorCodes.SECURITY_ERROR, cause.toString());
            return Messages.newError(requestId, JsonRpcErrorCodes.SECURITY_ERROR, cause.toString());
        } else {
            logger.errorf(cause, errorMessage, featureId);
            mcpRequest.setTracingErrorResponse(false, JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error");
            return Messages.newError(requestId, JsonRpcErrorCodes.INTERNAL_ERROR, "Internal error");
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
