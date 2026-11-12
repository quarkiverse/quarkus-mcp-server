package io.quarkiverse.mcp.server.http.runtime;

import java.util.List;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.ObservesAsync;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.http.runtime.McpServerEndpoints.McpServerEndpoint;
import io.quarkus.vertx.http.HttpServerStart;
import io.quarkus.vertx.http.HttpsServerStart;
import io.vertx.core.net.TcpServerConfig;

@Dependent
public class McpServerEndpointsLogger {
    // TODO this class assumes, that the server is running on HTTP 1, which may not be the case
    void onHttpServerStart(@ObservesAsync HttpServerStart start, McpServerEndpoints endpoints) {
        logEndpoints(endpoints.endpoints, start.config().getTcpConfig());
    }

    void onHttpsServerStart(@ObservesAsync HttpsServerStart start, McpServerEndpoints endpoints) {
        logEndpoints(endpoints.endpoints, start.config().getTcpConfig());
    }

    private void logEndpoints(List<McpServerEndpoint> endpoints, TcpServerConfig httpServerOptions) {
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
