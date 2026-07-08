package io.quarkiverse.mcp.server.test.cachecontrol;

import java.util.List;

import io.quarkiverse.mcp.server.CacheControl;
import io.quarkiverse.mcp.server.CacheScope;
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

public class CacheControlResources {

    @Resource(uri = "file:///cc/alpha", cacheControl = @Resource.CacheControl(ttlMs = 5000, cacheScope = CacheScope.PRIVATE))
    ResourceResponse alpha(RequestUri uri) {
        return new ResourceResponse(List.of(new TextResourceContents(uri.value(), "alpha", null)));
    }

    @Resource(uri = "file:///cc/bravo")
    ResourceResponse bravo(RequestUri uri) {
        return new ResourceResponse(List.of(new TextResourceContents(uri.value(), "bravo", null)));
    }

    @Resource(uri = "file:///cc/programmatic_override", cacheControl = @Resource.CacheControl(ttlMs = 1000, cacheScope = CacheScope.PUBLIC))
    ResourceResponse programmaticOverride(RequestUri uri) {
        // ResourceResponse cache control takes precedence over annotation
        return new ResourceResponse(List.of(new TextResourceContents(uri.value(), "override", null)),
                null, new CacheControl(9999, CacheScope.PRIVATE));
    }

    @ResourceTemplate(uriTemplate = "file:///cc/template/{id}", cacheControl = @Resource.CacheControl(ttlMs = 3000, cacheScope = CacheScope.PUBLIC))
    TextResourceContents template(String id, RequestUri uri) {
        return new TextResourceContents(uri.value(), "template-" + id, null);
    }

    @Tool
    TextContent ccTool(String input) {
        return new TextContent(input);
    }

    @Prompt
    PromptResponse ccPrompt() {
        return new PromptResponse("test", List.of(new PromptMessage(Role.USER, new TextContent("hello"))));
    }
}
