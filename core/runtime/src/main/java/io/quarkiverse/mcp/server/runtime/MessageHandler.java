package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.McpConnection;
import io.vertx.core.Future;

public abstract class MessageHandler {

    private static final Logger LOG = Logger.getLogger(MessageHandler.class);

    protected Future<Void> handleFailure(Object requestId, Sender sender, McpConnection connection, Throwable cause,
            Logger logger, String errorMessage, String featureId) {
        if (cause instanceof McpException mcp) {
            return sender.sendError(requestId, mcp.getJsonRpcError(), mcp.getMessage());
        } else if (cause instanceof Cancellation.OperationCancellationException) {
            LOG.debugf("Operation for request %s was cancelled", requestId);
            // Skip processing
            return Future.succeededFuture();
        } else if (Failures.isSecurityFailure(cause)) {
            return sender.sendError(requestId, JsonRPC.SECURITY_ERROR, cause.toString());
        } else {
            logger.errorf(cause, errorMessage, featureId);
            return sender.sendInternalError(requestId);
        }
    }

}
