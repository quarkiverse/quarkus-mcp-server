package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.PromptResponseEncoder;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class PromptCustomResponseEncoderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class, MyObject.class, MyObjectEncoder.class));

    @Test
    public void testEncoder() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .promptsGet("bravo", Map.of("price", "10"), r -> {
                    assertEquals("MyObject[name=foo, sum=20, valid=true]", r.messages().get(0).content().asText().text());
                })
                .thenAssertResults();
    }

    public record MyObject(String name, int sum, boolean valid) {

    }

    public static class MyPrompts {

        @Prompt
        MyObject bravo(String price) {
            return new MyObject("foo", Integer.parseInt(price) * 2, true);
        }

    }

    @Singleton
    @Priority(1)
    public static class MyObjectEncoder implements PromptResponseEncoder<MyObject> {

        @Override
        public boolean supports(Class<?> runtimeType) {
            return MyObject.class.equals(runtimeType);
        }

        @Override
        public PromptResponse encode(MyObject value) {
            return new PromptResponse(null, List.of(PromptMessage.withUserRole(new TextContent(value.toString()))));
        }

    }

}
