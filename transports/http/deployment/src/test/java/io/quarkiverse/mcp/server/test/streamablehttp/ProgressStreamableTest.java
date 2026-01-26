package io.quarkiverse.mcp.server.test.streamablehttp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CompletePrompt;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ProgressStreamableTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Inject
    MyTools tools;

    @Test
    public void testToolWithProgress() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        String token = "abcd";

        tools.init();

        client.when()
                // Tools
                .toolsCall("bravo")
                .withArguments(Map.of("price", 10))
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals("420", r.firstContent().asText().text());
                })
                .send()
                .toolsCall("bravoB")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals("bravo", r.firstContent().asText().text());
                })
                .send()
                // Prompts
                .promptsGet("charlie")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals(Role.ASSISTANT, r.firstMessage().role());
                    assertEquals("yes", r.firstMessage().content().asText().text());
                })
                .send()
                .promptsGet("charlieB")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals(Role.ASSISTANT, r.firstMessage().role());
                    assertEquals("yep", r.firstMessage().content().asText().text());
                })
                .send()
                // Resource templates
                .resourcesRead("file:///echo/ok")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals("foo=ok", r.firstContents().asText().text());
                })
                .send()
                .resourcesRead("file:///echos/ok")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals("echo", r.firstContents().asText().text());
                })
                .send()
                // Resources
                .resourcesRead("file:///foxtrot")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals("fox", r.firstContents().asText().text());
                })
                .send()
                .resourcesRead("file:///foxtrots")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals("foxes", r.firstContents().asText().text());
                })
                .send()
                // Completions
                .promptComplete("charlie")
                .withArgument("name", "Abc")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals("Dave", r.values().get(0));
                })
                .send()
                .promptComplete("charlieB")
                .withArgument("name", "Abc")
                .withMetadata(Map.of("progressToken", token))
                .withAssert(r -> {
                    assertEquals("David", r.values().get(0));
                })
                .send()
                .thenAssertResults();

        List<JsonObject> notifications = client.waitForNotifications(10).notifications();
        assertProgressNotification(notifications.get(0), token, 1, 1, null);
        assertProgressNotification(notifications.get(1), token, 1, 1, null);
        assertProgressNotification(notifications.get(2), token, 1, 1, null);
        assertProgressNotification(notifications.get(3), token, 1, 1, null);
        assertProgressNotification(notifications.get(4), token, 1, 1, null);
        assertProgressNotification(notifications.get(5), token, 1, 1, null);
        assertProgressNotification(notifications.get(6), token, 1, 1, null);
        assertProgressNotification(notifications.get(7), token, 1, 1, null);
        assertProgressNotification(notifications.get(8), token, 1, 1, null);
        assertProgressNotification(notifications.get(9), token, 1, 1, null);
    }

    protected void assertProgressNotification(JsonObject notification, String token, int progress, double total,
            String message) {
        JsonObject params = notification.getJsonObject("params");
        assertEquals(token, params.getString("progressToken"));
        assertEquals(progress, params.getInteger("progress"));
        assertEquals(total, params.getDouble("total"));
        assertEquals(message, params.getString("message"));
    }

    public static class MyTools {

        @Inject
        ToolManager toolManager;

        @Inject
        ResourceManager resourceManager;

        @Inject
        ResourceTemplateManager resourceTemplateManager;

        @Inject
        PromptManager promptManager;

        @Inject
        PromptCompletionManager promptCompletionManager;

        void init() {
            toolManager.newTool("bravoB")
                    .setDescription("BravoB")
                    .setHandler(args -> {
                        args.progress().notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
                        return ToolResponse.success("bravo");
                    })
                    .register();

            resourceManager.newResource("foxtrotB")
                    .setUri("file:///foxtrots")
                    .setDescription("FoxtrotB")
                    .setHandler(args -> {
                        args.progress().notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
                        return new ResourceResponse(TextResourceContents.create(args.requestUri().value(), "foxes"));
                    })
                    .register();

            resourceTemplateManager.newResourceTemplate("echoB")
                    .setUriTemplate("file:///echos/{foo}")
                    .setDescription("EchoB")
                    .setHandler(args -> {
                        args.progress().notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
                        return new ResourceResponse(TextResourceContents.create(args.requestUri().value(), "echo"));
                    })
                    .register();

            promptManager.newPrompt("charlieB")
                    .setDescription("CharlieB")
                    .addArgument("name", null, false)
                    .setHandler(args -> {
                        args.progress().notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
                        return PromptResponse.withMessages(PromptMessage.withAssistantRole("yep"));
                    })
                    .register();

            promptCompletionManager.newCompletion("charlieB")
                    .setArgumentName("name")
                    .setHandler(args -> {
                        args.progress().notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
                        return CompletionResponse.create("David");
                    }).register();
        }

        @Tool
        String bravo(int price, Progress progress) {
            progress.notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
            return "" + price * 42;
        }

        @Prompt
        PromptMessage charlie(@PromptArg(required = false) String name, Progress progress) {
            progress.notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
            return PromptMessage.withAssistantRole("yes");
        }

        @Resource(uri = "file:///foxtrot")
        TextResourceContents foxtrot(RequestUri uri, Progress progress) {
            progress.notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
            return TextResourceContents.create(uri.value(), "fox");
        }

        @ResourceTemplate(uriTemplate = "file:///echo/{foo}")
        TextResourceContents echo(String foo, RequestUri uri, Progress progress) {
            progress.notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
            return TextResourceContents.create(uri.value(), "foo=" + foo);
        }

        @CompletePrompt("charlie")
        String completeCharlie(String name, Progress progress) {
            progress.notificationBuilder().setTotal(1).setProgress(1).build().sendAndForget();
            return "Dave";
        }

    }

}
