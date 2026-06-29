package io.quarkiverse.mcp.server.test.devui;

import java.util.List;

import io.quarkiverse.mcp.server.CompleteArg;
import io.quarkiverse.mcp.server.CompletePrompt;
import io.quarkiverse.mcp.server.CompleteResourceTemplate;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class DevUIAppFeatures {

    @Tool
    String echo(@ToolArg(description = "The message to echo") String message) {
        return "Echo: " + message;
    }

    @Tool
    String add(int a, int b) {
        return String.valueOf(a + b);
    }

    @Prompt(description = "A greeting prompt")
    PromptMessage greet(@PromptArg(description = "The name to greet") String name) {
        return PromptMessage.withUserRole(new TextContent("Hello " + name));
    }

    @CompletePrompt("greet")
    List<String> completeGreetName(@CompleteArg(name = "name") String val) {
        return List.of("Alice", "Bob", "Charlie").stream()
                .filter(n -> n.toLowerCase().startsWith(val.toLowerCase())).toList();
    }

    @Resource(uri = "file:///test/info")
    TextResourceContents info(RequestUri uri) {
        return TextResourceContents.create(uri.value(), "Test info content");
    }

    @ResourceTemplate(uriTemplate = "file:///test/{name}")
    TextResourceContents testTemplate(String name, RequestUri uri) {
        return TextResourceContents.create(uri.value(), "Content for " + name);
    }

    @CompleteResourceTemplate("testTemplate")
    List<String> completeTemplateName(String name) {
        return List.of("alpha", "bravo", "charlie").stream()
                .filter(n -> n.startsWith(name)).toList();
    }
}
