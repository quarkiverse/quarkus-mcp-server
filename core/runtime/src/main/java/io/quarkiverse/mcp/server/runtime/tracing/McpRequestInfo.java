package io.quarkiverse.mcp.server.runtime.tracing;

import java.util.concurrent.atomic.AtomicReference;

import io.opentelemetry.context.Context;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.runtime.McpRequest;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.vertx.core.json.JsonObject;

public class McpRequestInfo {

    private final McpMethod method;
    private final JsonObject message;
    private final McpRequest mcpRequest;
    private final InitialRequest.Transport transport;
    // Shared between original and clones so response info set on a cloned context is visible to the span
    private final AtomicReference<McpResponseInfo> responseInfo;
    private Context ambientContext;

    public McpRequestInfo(McpMethod method, JsonObject message, McpRequest mcpRequest, InitialRequest.Transport transport) {
        this.method = method;
        this.message = message;
        this.mcpRequest = mcpRequest;
        this.transport = transport;
        this.responseInfo = new AtomicReference<>();
    }

    public McpMethod method() {
        return method;
    }

    public Object getRequestId() {
        return Messages.isRequest(message) ? Messages.getId(message) : null;
    }

    public String getToolName() {
        JsonObject params = Messages.getParams(message);
        return params != null ? params.getString("name") : null;
    }

    public String getPromptName() {
        JsonObject params = Messages.getParams(message);
        return params != null ? params.getString("name") : null;
    }

    public String getResourceUri() {
        JsonObject params = Messages.getParams(message);
        return params != null ? params.getString("uri") : null;
    }

    public String getSessionId() {
        return mcpRequest.connection().id();
    }

    public String getProtocolVersion() {
        return mcpRequest.protocolVersion();
    }

    public InitialRequest.Transport getTransport() {
        return transport;
    }

    public JsonObject getMeta() {
        JsonObject params = Messages.getParams(message);
        return params != null ? params.getJsonObject("_meta") : null;
    }

    public McpResponseInfo getResponseInfo() {
        return responseInfo.get();
    }

    public void setResponseInfo(McpResponseInfo responseInfo) {
        this.responseInfo.set(responseInfo);
    }

    public Context getAmbientContext() {
        return ambientContext;
    }

    void setAmbientContext(Context ambientContext) {
        this.ambientContext = ambientContext;
    }
}
