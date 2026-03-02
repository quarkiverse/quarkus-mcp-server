package io.quarkiverse.mcp.conformance;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.CompletePrompt;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.ProgressTracker;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;

/**
 * MCP Conformance Test Server
 */
public class ConformanceServer {

    @Inject
    ResourceManager resourceManager;

    @Inject
    ToolManager toolManager;

    @Inject
    PromptManager promptManager;

    @Inject
    ScheduledExecutorService executorService;

    // Sample base64 encoded 1x1 red PNG pixel for testing
    private static final String TEST_IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    // Sample base64 encoded minimal WAV file for testing
    private static final String TEST_AUDIO_BASE64 = "UklGRiYAAABXQVZFZm10IBAAAAABAAEAQB8AAAB9AAACABAAZGF0YQIAAAA=";

    private final AtomicInteger watchedResourceCounter = new AtomicInteger();

    // ===== COMPLETION =====

    @CompletePrompt("test_prompt_with_arguments")
    List<String> complete(String arg1) {
        if (arg1 != null) {
            return List.of();
        }
        throw new IllegalStateException();
    }

    // ===== TOOLS =====

    @Tool(description = "Tests simple text content response")
    TextContent test_simple_text() {
        return new TextContent("This is a simple text response for testing.");
    }

    @Tool(description = "Tests image content response")
    ImageContent test_image_content() {
        return new ImageContent(TEST_IMAGE_BASE64, "image/png");
    }

    @Tool(description = "Tests audio content response")
    AudioContent test_audio_content() {
        return new AudioContent(TEST_AUDIO_BASE64, "audio/wav");
    }

    @Tool(description = "Tests embedded resource content response")
    EmbeddedResource test_embedded_resource() {
        return new EmbeddedResource(
                new TextResourceContents("test://embedded-resource", "This is an embedded resource content.", "text/plain"));
    }

    @Tool(description = "Tests response with multiple content types (text, image, resource)")
    ToolResponse test_multiple_content_types() {
        return ToolResponse.success(List.of(
                new TextContent("Multiple content types test:"),
                new ImageContent(TEST_IMAGE_BASE64, "image/png"),
                new EmbeddedResource(
                        new TextResourceContents("test://mixed-content-resource", "{\"test\":\"data\",\"value\":123}",
                                "application/json"))));
    }

    @Tool(description = "Tests tool that emits log messages during execution")
    Uni<TextContent> test_tool_with_logging(McpLog log) {
        return Uni.createFrom().item(() -> {
            log.info("Tool execution started");
            return null;
        })
                .onItem().delayIt().by(Duration.ofMillis(50))
                .onItem().invoke(() -> log.info("Tool processing data"))
                .onItem().delayIt().by(Duration.ofMillis(50))
                .onItem().invoke(() -> log.info("Tool execution completed"))
                .onItem().transform(v -> new TextContent("Tool with logging executed successfully"));
    }

    @Tool(description = "Tests tool that reports progress notifications")
    Uni<TextContent> test_tool_with_progress(Progress progress) {
        if (progress.token().isEmpty()) {
            return Uni.createFrom().item(new TextContent("No progress token provided"));
        }

        ProgressTracker tracker = progress.trackerBuilder()
                .setTotal(100)
                .setMessageBuilder(val -> "Completed step " + val + " of 100")
                .build();

        return tracker.advance(BigDecimal.ZERO)
                .onItem().delayIt().by(Duration.ofMillis(50))
                .chain(() -> tracker.advance(new BigDecimal(50)))
                .onItem().delayIt().by(Duration.ofMillis(50))
                .chain(() -> tracker.advance(new BigDecimal(50)))
                .onItem().transform(v -> new TextContent(progress.token().get().asString()));
    }

    @Tool(description = "Tests error response handling")
    TextContent test_error_handling() {
        throw new ToolCallException("This tool intentionally returns an error for testing");
    }

    @Tool(description = "Tests server-initiated sampling (LLM completion request)")
    Uni<TextContent> test_sampling(
            @ToolArg(description = "The prompt to send to the LLM") String prompt,
            Sampling sampling) {
        if (!sampling.isSupported()) {
            return Uni.createFrom().item(new TextContent("Sampling not supported or error: Client does not support sampling"));
        }

        return sampling.requestBuilder()
                .addMessage(SamplingMessage.withUserRole(prompt))
                .setMaxTokens(100)
                .build()
                .send()
                .map(response -> {
                    String modelResponse = "No response";
                    if (response.content() != null && response.content() instanceof TextContent textContent) {
                        modelResponse = textContent.text();
                    }
                    return new TextContent("LLM response: " + modelResponse);
                })
                .onFailure().recoverWithItem(err -> new TextContent("Sampling not supported or error: " + err.getMessage()));
    }

    @Tool(description = "Tests server-initiated elicitation (user input request)")
    Uni<TextContent> test_elicitation(
            @ToolArg(description = "The message to show the user") String message,
            Elicitation elicitation) {
        if (!elicitation.isSupported()) {
            return Uni.createFrom()
                    .item(new TextContent("Elicitation not supported or error: Client does not support elicitation"));
        }

        return elicitation.requestBuilder()
                .setMessage(message)
                .addSchemaProperty("response",
                        new ElicitationRequest.StringSchema(null, "User's response", null, null, null, true))
                .build()
                .send()
                .map(response -> {
                    String text = "User response: action=" + response.action() + ", content="
                            + new JsonObject(response.content().asMap()).encode();
                    return new TextContent(text);
                })
                .onFailure().recoverWithItem(err -> new TextContent("Elicitation not supported or error: " + err.getMessage()));
    }

    @Tool(description = "Tests elicitation with default values per SEP-1034")
    Uni<TextContent> test_elicitation_sep1034_defaults(Elicitation elicitation) {
        if (!elicitation.isSupported()) {
            return Uni.createFrom().item(new TextContent("Elicitation not supported"));
        }

        return elicitation.requestBuilder()
                .setMessage("Please review and update the form fields with defaults")
                .addSchemaProperty("name",
                        new ElicitationRequest.StringSchema(null, "User name", null, null, null, false, "John Doe"))
                .addSchemaProperty("age",
                        new ElicitationRequest.IntegerSchema(null, "User age", null, null, false, 30))
                .addSchemaProperty("score",
                        new ElicitationRequest.NumberSchema(null, "User score", null, null, false, 95.5))
                .addSchemaProperty("status",
                        new ElicitationRequest.EnumSchema(null, "User status", List.of("active", "inactive", "pending"), null,
                                false, "active"))
                .addSchemaProperty("verified",
                        new ElicitationRequest.BooleanSchema(null, "Verification status", true, false))
                .build()
                .send()
                .map(response -> {
                    String text = "Elicitation completed: action=" + response.action() + ", content="
                            + new JsonObject(response.content().asMap()).encode();
                    return new TextContent(text);

                })
                .onFailure().recoverWithItem(err -> new TextContent("Elicitation not supported or error: " + err.getMessage()));
    }

    @Tool(description = "Test elicitation with enum schema improvements (SEP-1330)")
    Uni<TextContent> test_elicitation_sep1330_enums(Elicitation elicitation) {
        if (!elicitation.isSupported()) {
            return Uni.createFrom().item(new TextContent("Elicitation not supported"));
        }

        return elicitation.requestBuilder()
                .setMessage("Please review and update the form fields with defaults")
                .addSchemaProperty("untitledSingle",
                        new ElicitationRequest.SingleSelectEnumSchema(null, "Select one option",
                                List.of("option1", "option2", "option3"), null, false, null))
                .addSchemaProperty("titledSingle",
                        new ElicitationRequest.SingleSelectEnumSchema(null, "Select one option with titles",
                                List.of("value1", "value2", "value3"), List.of("First Option", "Second Option", "Third Option"),
                                false, null))
                .addSchemaProperty("legacyEnum", new ElicitationRequest.EnumSchema(null, "Select one option (legacy)",
                        List.of("opt1", "opt2", "opt3"), List.of("Option One", "Option Two", "Option Three"), false))
                .addSchemaProperty("untitledMulti",
                        new ElicitationRequest.MultiSelectEnumSchema(null, "Select multiple options",
                                List.of("option1", "option2", "option3"), null, 1, 3, false, null))
                .addSchemaProperty("titledMulti",
                        new ElicitationRequest.MultiSelectEnumSchema(null, "Select multiple options with titles",
                                List.of("value1", "value2", "value3"), List.of("First Choice", "Second Choice", "Third Choice"),
                                1, 3, false, null))
                .build()
                .send()
                .map(response -> {
                    String text = "Elicitation completed: action=" + response.action() + ", content="
                            + new JsonObject(response.content().asMap()).encode();
                    return new TextContent(text);

                })
                .onFailure().recoverWithItem(err -> new TextContent("Elicitation not supported or error: " + err.getMessage()));
    }

    // ===== RESOURCES =====

    @Resource(uri = "test://static-text", title = "Static Text Resource", description = "A static text resource for testing", mimeType = "text/plain")
    String static_text(RequestUri uri) {
        return "This is the content of the static text resource.";
    }

    @Resource(uri = "test://static-binary", title = "Static Binary Resource", description = "A static binary resource (image) for testing", mimeType = "image/png")
    BlobResourceContents static_binary(RequestUri uri) {
        return new BlobResourceContents(uri.value(), TEST_IMAGE_BASE64, "image/png");
    }

    @ResourceTemplate(uriTemplate = "test://template/{id}/data", title = "Resource Template", description = "A resource template with parameter substitution", mimeType = "application/json")
    TextResourceContents template(String id, RequestUri uri) {
        String json = String.format("{\"id\":\"%s\",\"templateTest\":true,\"data\":\"Data for ID: %s\"}", id, id);
        return TextResourceContents.create(uri.value(), json);
    }

    @Resource(uri = "test://watched-resource", title = "Watched Resource", description = "A resource that auto-updates every 3 seconds", mimeType = "text/plain")
    TextResourceContents watched_resource(RequestUri uri) {
        return TextResourceContents.create(uri.value(), "Watched resource content: " + watchedResourceCounter.get());
    }

    // Update watched resource every 3 seconds
    @Scheduled(every = "3s")
    void updateWatchedResource() {
        watchedResourceCounter.incrementAndGet();
        ResourceManager.ResourceInfo resource = resourceManager.getResource("test://watched-resource");
        if (resource != null) {
            resource.sendUpdateAndForget();
        }
    }

    // ===== PROMPTS =====

    @Prompt(title = "Simple Test Prompt", description = "A simple prompt without arguments")
    PromptMessage test_simple_prompt() {
        return PromptMessage.withUserRole(new TextContent("This is a simple prompt for testing."));
    }

    @Prompt(title = "Prompt With Arguments", description = "A prompt with required arguments")
    PromptMessage test_prompt_with_arguments(
            @PromptArg(description = "First test argument") String arg1,
            @PromptArg(description = "Second test argument") String arg2) {
        return PromptMessage.withUserRole(
                new TextContent("Prompt with arguments: arg1='" + arg1 + "', arg2='" + arg2 + "'"));
    }

    @Prompt(title = "Prompt With Embedded Resource", description = "A prompt that includes an embedded resource")
    List<PromptMessage> test_prompt_with_embedded_resource(
            @PromptArg(description = "URI of the resource to embed") String resourceUri) {
        return List.of(
                PromptMessage.withUserRole(
                        new EmbeddedResource(new TextResourceContents(resourceUri,
                                "Embedded resource content for testing.", "text/plain"))),
                PromptMessage.withUserRole(new TextContent("Please process the embedded resource above.")));
    }

    @Prompt(title = "Prompt With Image", description = "A prompt that includes image content")
    List<PromptMessage> test_prompt_with_image() {
        return List.of(
                PromptMessage.withUserRole(new ImageContent(TEST_IMAGE_BASE64, "image/png")),
                PromptMessage.withUserRole(new TextContent("Please analyze the image above.")));
    }

    // Register dynamic tools, resources, and prompts after 2 seconds
    @Startup
    void registerDynamicFeatures() {
        executorService.schedule(() -> {
            // Register dynamic tool
            toolManager.newTool("test_dynamic_tool")
                    .setDescription("A tool that was dynamically added after server startup")
                    .setHandler(args -> ToolResponse
                            .success(new TextContent("This tool was added dynamically 2 seconds after startup")))
                    .register();

            // Register dynamic resource
            resourceManager.newResource("test_dynamic_resource")
                    .setUri("test://dynamic-resource")
                    .setTitle("Dynamic Resource")
                    .setDescription("A resource that was dynamically added after server startup")
                    .setMimeType("text/plain")
                    .setHandler(args -> new ResourceResponse(List.of(
                            TextResourceContents.create(args.requestUri().value(),
                                    "This resource was added dynamically 2 seconds after startup"))))
                    .register();

            // Register dynamic prompt
            promptManager.newPrompt("test_dynamic_prompt")
                    .setTitle("Dynamic Prompt")
                    .setDescription("A prompt that was dynamically added after server startup")
                    .setHandler(args -> PromptResponse.withMessages(PromptMessage.withUserRole(
                            new TextContent("This prompt was added dynamically 2 seconds after startup"))))
                    .register();
        }, 2, TimeUnit.SECONDS);
    }
}
