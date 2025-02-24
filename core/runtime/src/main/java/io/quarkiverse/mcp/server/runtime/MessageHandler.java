package io.quarkiverse.mcp.server.runtime;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpConnection;

public abstract class MessageHandler {

    protected void handleFailure(Object requestId, Responder responder, McpConnection connection, Throwable cause,
            Logger logger, String errorMessage, String featureId) {
        if (cause instanceof McpException mcp) {
            responder.sendError(requestId, mcp.getJsonRpcError(), mcp.getMessage());
        } else if (Failures.isSecurityFailure(cause)) {
            responder.sendError(requestId, JsonRPC.SECURITY_ERROR, cause.toString());
        } else {
            logger.errorf(cause, errorMessage, featureId);
            responder.sendInternalError(requestId);
        }
    }

}
