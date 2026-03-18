package io.quarkiverse.mcp.server.schema.validator.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CompleteArg;
import io.quarkiverse.mcp.server.CompletePrompt;
import io.quarkiverse.mcp.server.CompleteResourceTemplate;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;

public class CompletionSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class, MyResourceTemplates.class));

    @Test
    public void testCompletionComplete() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // Valid prompt completion
                .promptComplete("greeting", "name", "Ma", completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("Martin", completionResponse.values().get(0));
                })
                // Valid resource template completion
                .resourceTemplateComplete("file:///{foo}", "foo", "Ma", completionResponse -> {
                    assertEquals(1, completionResponse.values().size());
                    assertEquals("Martin", completionResponse.values().get(0));
                })
                // Send a completion/complete request without required "params" field
                .message(client.newRequest("completion/complete"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();
    }

    public static class MyPrompts {

        static final List<String> NAMES = List.of("Martin", "Lucy", "Jachym");

        @Prompt
        PromptMessage greeting(String name) {
            return PromptMessage.withUserRole("Hello " + name + "!");
        }

        @CompletePrompt("greeting")
        List<String> completeName(@CompleteArg(name = "name") String val) {
            return NAMES.stream().filter(n -> n.startsWith(val)).toList();
        }
    }

    public static class MyResourceTemplates {

        static final List<String> NAMES = List.of("Martin", "Lucy", "Jachym");

        @ResourceTemplate(uriTemplate = "file:///{foo}")
        TextResourceContents fooTemplate(String foo, RequestUri uri) {
            return TextResourceContents.create(uri.value(), foo);
        }

        @CompleteResourceTemplate("fooTemplate")
        List<String> completeFoo(@CompleteArg(name = "foo") String val) {
            return NAMES.stream().filter(n -> n.startsWith(val)).toList();
        }
    }
}
