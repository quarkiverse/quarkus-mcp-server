package io.quarkiverse.mcp.server.runtime;

import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.CacheScope;
import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.InputRequiredException;
import io.quarkiverse.mcp.server.InputRequiredException.ElicitationInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.InputRequestEntry;
import io.quarkiverse.mcp.server.InputRequiredException.RootsInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.SamplingInputRequest;
import io.quarkiverse.mcp.server.InputRequiredException.UrlElicitationInputRequest;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.UrlElicitationRequiredException;
import io.quarkiverse.mcp.server.UrlElicitationRequiredException.ElicitationEntry;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class MessageHandler {

    private static final Logger LOG = Logger.getLogger(MessageHandler.class);

    protected Future<Void> handleFailure(Object requestId, Sender sender, McpRequest mcpRequest, Throwable cause,
            Logger logger, String errorMessage, String featureId) {
        if (cause instanceof InputRequiredException inputRequired) {
            JsonObject result = new JsonObject().put("resultType", "input_required");
            if (!inputRequired.inputRequests().isEmpty()) {
                JsonObject inputRequests = new JsonObject();
                for (Map.Entry<String, InputRequestEntry> e : inputRequired.inputRequests().entrySet()) {
                    inputRequests.put(e.getKey(), serializeInputRequest(e.getValue()));
                }
                result.put("inputRequests", inputRequests);
            }
            if (inputRequired.requestState() != null) {
                result.put("requestState", inputRequired.requestState());
            }
            return sender.sendResult(requestId, result);
        } else if (cause instanceof UrlElicitationRequiredException urlElicitation) {
            mcpRequest.setTracingErrorResponse(false, urlElicitation.getJsonRpcErrorCode(), urlElicitation.getMessage());
            JsonArray elicitations = new JsonArray();
            for (ElicitationEntry entry : urlElicitation.elicitations()) {
                elicitations.add(new JsonObject()
                        .put("mode", "url")
                        .put("elicitationId", entry.elicitationId())
                        .put("url", entry.url())
                        .put("message", entry.message()));
            }
            JsonObject data = new JsonObject().put("elicitations", elicitations);
            return sender.send(
                    Messages.newError(requestId, urlElicitation.getJsonRpcErrorCode(), urlElicitation.getMessage(), data));
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

    private static JsonObject serializeInputRequest(InputRequestEntry entry) {
        if (entry instanceof ElicitationInputRequest e) {
            return ((ElicitationRequestImpl) e.request()).toInputRequestJson();
        } else if (entry instanceof UrlElicitationInputRequest e) {
            return ((UrlElicitationRequestImpl) e.request()).toInputRequestJson();
        } else if (entry instanceof SamplingInputRequest e) {
            return ((SamplingRequestImpl) e.request()).toInputRequestJson();
        } else if (entry instanceof RootsInputRequest) {
            return new JsonObject()
                    .put("method", McpMethod.ROOTS_LIST.jsonRpcName())
                    .put("params", new JsonObject());
        }
        throw new IllegalArgumentException("Unknown input request entry type: " + entry.getClass());
    }

    static void putCacheControl(JsonObject result, long ttlMs, Optional<CacheScope> cacheScope) {
        if (ttlMs >= 0) {
            result.put("ttlMs", ttlMs);
        }
        if (cacheScope.isPresent()) {
            result.put("cacheScope", cacheScope.get().getName());
        }
    }
}
