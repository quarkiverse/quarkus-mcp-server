package io.quarkiverse.mcp.server.stdio.it;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.logging.Log;

public class ServerFeatures {

    @Inject
    CodeService codeService;

    @Inject
    ToolManager toolManager;

    @Tool
    TextContent toLowerCase(String value) {
        // Just register another tool
        toolManager.newTool("testTool")
                .setDescription("This tool does nothing.")
                .setHandler(
                        ta -> ToolResponse.success("OK"))
                .register();
        return new TextContent(value.toLowerCase());
    }

    @Prompt(name = "code_assist")
    PromptMessage codeAssist(@PromptArg(name = "lang") String language) {
        Log.info("Log from code assist...");
        return PromptMessage.withUserRole(new TextContent(codeService.assist(language)));
    }

    @Resource(uri = "file:///project/alpha")
    BlobResourceContents alpha(RequestUri uri) {
        return BlobResourceContents.create(uri.value(), "data".getBytes());
    }

    @Tool
    String protocolInfo(McpConnection connection) {
        String version = connection.initialRequest().protocolVersion();
        boolean stateless = McpProtocolVersion.isStateless(version);
        return version + ":" + stateless + ":" + connection.isTransient();
    }

    @Tool
    String logLevelInfo(McpConnection connection) {
        return connection.logLevel().name();
    }

}
