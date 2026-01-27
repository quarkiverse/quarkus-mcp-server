package io.quarkiverse.mcp.server.hibernate.validator.test;

import static io.quarkiverse.mcp.server.McpServer.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;

public class PromptConstraintViolationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Inject
    MyPrompts prompts;

    @Test
    public void testError() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .promptsGet("bravo", Map.of("to", "mkouba@redhat.com"), promptResponse -> {
                    assertEquals("to:mkouba@redhat.com", promptResponse.firstMessage().content().asText().text());
                })
                .promptsGet("bravo")
                .withArguments(Map.of("to", "not an email"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertEquals("bravo.to: must be a well-formed email address", error.message());
                })
                .send()
                .thenAssertResults();

        assertThrows(McpException.class, () -> prompts.bravo("also not an email"));
        assertThrows(ConstraintViolationException.class, () -> prompts.nonPromptBravo("foo	"));
    }

    @McpServer(DEFAULT)
    public static class MyPrompts {

        @Prompt
        PromptMessage bravo(@Email String to) {
            return PromptMessage.withAssistantRole("to:" + to);
        }

        String nonPromptBravo(@Email String to) {
            return "to" + to;
        }

    }

}
