package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.RequestId;
import io.vertx.core.json.JsonObject;

final class FilterContextImpl implements FilterContext {

    static FilterContextImpl of(McpMethod method, JsonObject message, McpRequest mcpRequest) {
        return new FilterContextImpl(method, message, mcpRequest);
    }

    final McpMethod method;
    final JsonObject message;
    final McpRequest mcpRequest;

    private FilterContextImpl(McpMethod method, JsonObject message, McpRequest mcpRequest) {
        this.method = Objects.requireNonNull(method);
        this.message = message;
        this.mcpRequest = Objects.requireNonNull(mcpRequest);
    }

    @Override
    public McpMethod method() {
        return method;
    }

    @Override
    public McpConnection connection() {
        return mcpRequest.connection();
    }

    @Override
    public Meta meta() {
        return message != null ? MetaImpl.from(Messages.getParams(message)) : MetaImpl.from(null);
    }

    @Override
    public RequestId requestId() {
        return message != null ? new RequestId(Messages.getId(message)) : null;
    }

}
