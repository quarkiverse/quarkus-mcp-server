package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.vertx.core.Future;

public abstract class MessageHandler {

    private static final Logger LOG = Logger.getLogger(MessageHandler.class);

    protected Future<Void> handleFailure(Object requestId, Sender sender, McpRequest mcpRequest, Throwable cause,
            Logger logger, String errorMessage, String featureId) {
        if (cause instanceof McpException mcp) {
            mcpRequest.setTracingErrorResponse(false, mcp.getJsonRpcErrorCode(), mcp.getMessage());
            return sender.sendError(requestId, mcp.getJsonRpcErrorCode(), mcp.getMessage());
        } else if (cause instanceof Cancellation.OperationCancellationException) {
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
}
