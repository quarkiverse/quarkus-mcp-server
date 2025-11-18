package io.quarkiverse.mcp.server.sse.runtime;

import java.util.List;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.ObservesAsync;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.sse.runtime.McpServerEndpoints.McpServerEndpoint;
import io.quarkus.vertx.http.HttpServerStart;
import io.quarkus.vertx.http.HttpsServerStart;
import io.vertx.core.http.HttpServerOptions;

@Dependent
public class McpServerEndpointsLogger {

    void onHttpServerStart(@ObservesAsync HttpServerStart start, McpServerEndpoints endpoints) {
        logEndpoints(endpoints.endpoints, start.options());
    }

    void onHttpsServerStart(@ObservesAsync HttpsServerStart start, McpServerEndpoints endpoints) {
        logEndpoints(endpoints.endpoints, start.options());
    }

    private void logEndpoints(List<McpServerEndpoint> endpoints, HttpServerOptions httpServerOptions) {
        Logger log = Logger.getLogger("io.quarkiverse.mcp.server");
        // base is scheme://host:port
        String base = new StringBuilder(httpServerOptions.isSsl() ? "https://" : "http://")
                .append(httpServerOptions.getHost())
                .append(":")
                .append(httpServerOptions.getPort())
                .toString();
        for (McpServerEndpoints.McpServerEndpoint e : endpoints) {
            String serverInfo = "";
            if (!McpServer.DEFAULT.equals(e.serverName)) {
                serverInfo = " [" + e.serverName + "]";
            }
            log.infof("MCP%s HTTP transport endpoints [streamable: %s, SSE: %s]", serverInfo, base + e.mcpPath,
                    base + e.ssePath);
        }

    }

}
