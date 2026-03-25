package io.quarkiverse.mcp.server.runtime;

import java.util.Optional;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.RequestId;

public class CancellationImpl implements Cancellation {

    static CancellationImpl from(ArgumentProviders argProviders) {
        return new CancellationImpl(argProviders.connection(), argProviders.requestId(), argProviders.cancellationRequests());
    }

    private final McpConnectionBase connection;
    private final RequestId requestId;
    private final CancellationRequests cancellationRequests;

    private CancellationImpl(McpConnectionBase connection, Object requestId, CancellationRequests cancellationRequests) {
        this.connection = connection;
        this.requestId = new RequestId(requestId);
        this.cancellationRequests = cancellationRequests;
    }

    @Override
    public Result check() {
        Optional<String> reason = cancellationRequests.get(connection, requestId);
        if (reason == null) {
            return new Result(false, Optional.empty());
        } else {
            return new Result(true, reason);
        }
    }

}
