package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;
import io.vertx.core.Future;

public abstract class MessageHandler {

    protected Future<Void> handleFailure(Object requestId, Sender sender, McpConnection connection, Throwable cause,
            Logger logger, String errorMessage, String featureId) {
        if (cause instanceof McpException mcp) {
            return sender.sendError(requestId, mcp.getJsonRpcError(), mcp.getMessage());
        } else if (Failures.isSecurityFailure(cause)) {
            return sender.sendError(requestId, JsonRPC.SECURITY_ERROR, cause.toString());
        } else {
            logger.errorf(cause, errorMessage, featureId);
            return sender.sendInternalError(requestId);
        }
    }

}
