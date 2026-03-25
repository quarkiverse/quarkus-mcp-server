package io.quarkiverse.mcp.server.runtime;

import io.quarkiverse.mcp.server.runtime.tracing.McpRequestInfo;

public interface McpTracingSpan {

    /**
     * Returns request info, or null if tracing is inactive.
     */
    McpRequestInfo requestInfo();

    /**
     * Ends the span. Safe to call even if tracing is inactive.
     */
    void end(Throwable error);

    McpTracingSpan NOOP = new McpTracingSpan() {
        @Override
        public McpRequestInfo requestInfo() {
            return null;
        }

        @Override
        public void end(Throwable error) {
        }
    };
}
