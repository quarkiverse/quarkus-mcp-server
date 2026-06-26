package io.quarkiverse.mcp.server.runtime.mcpjava;

import java.util.Optional;

import org.mcpjava.server.Cancellation;
import org.mcpjava.server.Cancellation.OperationCancelledException;

import io.quarkiverse.mcp.server.Cancellation.OperationCancellationException;
import io.quarkiverse.mcp.server.runtime.ArgumentProviders;
import io.quarkiverse.mcp.server.runtime.CancellationImpl;

public class McpJavaCancellationAdapter implements Cancellation {

    private final io.quarkiverse.mcp.server.Cancellation delegate;

    McpJavaCancellationAdapter(io.quarkiverse.mcp.server.Cancellation delegate) {
        this.delegate = delegate;
    }

    public static McpJavaCancellationAdapter from(ArgumentProviders argProviders) {
        return new McpJavaCancellationAdapter(CancellationImpl.from(argProviders));
    }

    @Override
    public Result check() {
        io.quarkiverse.mcp.server.Cancellation.Result result = delegate.check();
        return new ResultAdapter(result);
    }

    @Override
    public void skipProcessingIfCancelled() {
        try {
            delegate.skipProcessingIfCancelled();
        } catch (OperationCancellationException e) {
            throw new OperationCancelledException(e.getMessage());
        }
    }

    record ResultAdapter(io.quarkiverse.mcp.server.Cancellation.Result delegate) implements Result {

        @Override
        public boolean isRequested() {
            return delegate.isRequested();
        }

        @Override
        public Optional<String> reason() {
            return delegate.reason();
        }
    }
}
