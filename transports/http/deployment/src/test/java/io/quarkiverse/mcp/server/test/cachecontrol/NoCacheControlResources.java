package io.quarkiverse.mcp.server.test.cachecontrol;

import java.util.List;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;

public class NoCacheControlResources {

    @Resource(uri = "file:///nocc/alpha")
    ResourceResponse alpha(RequestUri uri) {
        return new ResourceResponse(List.of(new TextResourceContents(uri.value(), "alpha", null)));
    }

    @ResourceTemplate(uriTemplate = "file:///nocc/template/{id}")
    TextResourceContents template(String id, RequestUri uri) {
        return new TextResourceContents(uri.value(), "template-" + id, null);
    }

    @Tool
    TextContent noccTool(String input) {
        return new TextContent(input);
    }

    @Prompt
    PromptResponse noccPrompt() {
        return new PromptResponse("test", List.of(new PromptMessage(Role.USER, new TextContent("hello"))));
    }
}
