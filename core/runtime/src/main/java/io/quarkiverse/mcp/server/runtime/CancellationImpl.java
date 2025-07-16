package io.quarkiverse.mcp.server.runtime;

import java.util.Optional;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.RequestId;

public class CancellationImpl implements Cancellation {

    static CancellationImpl from(ArgumentProviders argProviders) {
        return new CancellationImpl(argProviders.connection(), argProviders.requestId());
    }

    private final McpConnectionBase connection;
    private final RequestId requestId;

    private CancellationImpl(McpConnectionBase connection, Object requestId) {
        this.connection = connection;
        this.requestId = new RequestId(requestId);
    }

    @Override
    public Result check() {
        Optional<String> reason = connection.getCancellationRequest(requestId);
        if (reason == null) {
            return new Result(false, Optional.empty());
        } else {
            return new Result(true, reason);
        }
    }

}
