package io.quarkiverse.mcp.server.test.mcpjava;

import static io.quarkiverse.mcp.server.test.Checks.checkDuplicatedContext;
import static io.quarkiverse.mcp.server.test.Checks.checkExecutionModel;
import static io.quarkiverse.mcp.server.test.Checks.checkRequestContext;

import java.util.List;
import java.util.Optional;

import org.mcpjava.server.Role;
import org.mcpjava.server.completion.CompleteArg;
import org.mcpjava.server.completion.CompletePrompt;
import org.mcpjava.server.completion.CompleteResourceTemplate;
import org.mcpjava.server.completion.CompletionResult;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.prompts.PromptResponse;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.mcpjava.server.tools.ToolResponse;

import io.smallrye.mutiny.Uni;

public class McpJavaFeatures {

    // --- Tools ---

    @Tool(description = "A simple tool")
    String alpha(@ToolArg(description = "The price") Optional<Integer> price) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return "alpha:" + price.orElse(0);
    }

    @Tool(title = "Bravo Tool")
    Uni<String> bravo(@ToolArg(name = "val") String value) {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item("bravo:" + value);
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false))
    List<String> charlie() {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return List.of("charlie1", "charlie2");
    }

    @Tool(description = "Returns TextContent")
    TextContent delta(@ToolArg(description = "The text") String text) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return TextContent.of("delta:" + text);
    }

    @Tool(description = "Returns ToolResponse")
    ToolResponse echo(@ToolArg(description = "The message") String msg) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return ToolResponse.ofText("echo:" + msg);
    }

    // --- Prompts ---

    @Prompt(description = "A greeting prompt", title = "Greeting")
    String greet(@PromptArg(description = "The name", title = "Name") String name) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return "Hello " + name + "!";
    }

    @Prompt(name = "farewell")
    Uni<String> farewell_prompt(@PromptArg(name = "name") String val) {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item("Goodbye " + val + "!");
    }

    @Prompt(description = "Returns PromptResponse")
    PromptResponse info(@PromptArg(description = "The topic") String topic) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return PromptResponse.of(Role.ASSISTANT, TextContent.of("info:" + topic));
    }

    // --- Resources ---

    @Resource(uri = "file:///mcpjava/alpha", title = "Alpha Resource", size = 10, annotations = @Resource.Annotations(audience = org.mcpjava.server.Role.USER, priority = 0.8))
    String resourceAlpha() {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return "resource-alpha";
    }

    @Resource(uri = "file:///mcpjava/bravo")
    Uni<String> resourceBravo() {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item("resource-bravo");
    }

    @Resource(uri = "file:///mcpjava/charlie")
    ResourceResponse resourceCharlie() {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return ResourceResponse.of("file:///mcpjava/charlie", "resource-charlie");
    }

    // --- Resource Templates ---

    @ResourceTemplate(uriTemplate = "file:///mcpjava/{path}", title = "File Template")
    String fileTemplate(@ResourceTemplateArg(name = "path") String path) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return "file:" + path;
    }

    // --- Completions ---

    @CompletePrompt("greet")
    List<String> completeGreetName(@CompleteArg(name = "name") String val) {
        return List.of("Martin", "Matej", "Michal").stream().filter(n -> n.startsWith(val)).toList();
    }

    @CompleteResourceTemplate("fileTemplate")
    List<String> completeFilePath(@CompleteArg(name = "path") String val) {
        return List.of("README.md", "pom.xml", "src").stream().filter(n -> n.startsWith(val)).toList();
    }

    @CompletePrompt("info")
    CompletionResult completeInfoTopic(@CompleteArg(name = "topic") String val) {
        List<String> topics = List.of("quarkus", "java", "mcp").stream()
                .filter(t -> t.startsWith(val)).toList();
        return CompletionResult.newCompleteResult(topics);
    }
}
