package io.quarkiverse.mcp.server.http.runtime;

import java.util.List;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.ObservesAsync;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.http.runtime.McpServerEndpoints.McpServerEndpoint;
import io.quarkus.vertx.http.HttpServerStart;
import io.quarkus.vertx.http.HttpsServerStart;
import io.vertx.core.http.HttpServerConfig;

@Dependent
public class McpServerEndpointsLogger {

    void onHttpServerStart(@ObservesAsync HttpServerStart start, McpServerEndpoints endpoints) {
        logEndpoints(endpoints.endpoints, baseUri(start));
    }

    void onHttpsServerStart(@ObservesAsync HttpsServerStart start, McpServerEndpoints endpoints) {
        logEndpoints(endpoints.endpoints, baseUri(start));
    }

    static String baseUri(HttpServerStart start) {
        return baseUri(start.config(), false);
    }

    static String baseUri(HttpsServerStart start) {
        // Note that we cannot rely on HttpServerConfig#getTcpConfig().isSsl() because in Vert.x 5
        // the TLS is derived from the ServerSSLOptions passed when the server is created
        return baseUri(start.config(), true);
    }

    private static String baseUri(HttpServerConfig config, boolean ssl) {
        // base is scheme://host:port
        return new StringBuilder(ssl ? "https://" : "http://")
                .append(config.getTcpHost())
                .append(":")
                .append(config.getTcpPort())
                .toString();
    }

    private void logEndpoints(List<McpServerEndpoint> endpoints, String base) {
        Logger log = Logger.getLogger("io.quarkiverse.mcp.server");
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
