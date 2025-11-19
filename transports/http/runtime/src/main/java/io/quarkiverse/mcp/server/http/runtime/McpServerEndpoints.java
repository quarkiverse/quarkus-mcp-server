package io.quarkiverse.mcp.server.http.runtime;

import java.util.List;

public class McpServerEndpoints {

    final List<McpServerEndpoints.McpServerEndpoint> endpoints;

    McpServerEndpoints(List<McpServerEndpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public static class McpServerEndpoint {

        public String serverName;
        public String mcpPath;
        public String ssePath;

        public McpServerEndpoint(String serverName, String mcpPath, String ssePath) {
            this.serverName = serverName;
            this.mcpPath = mcpPath;
            this.ssePath = ssePath;
        }

    }

}
