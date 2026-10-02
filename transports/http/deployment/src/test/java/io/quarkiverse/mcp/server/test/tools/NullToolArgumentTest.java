package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class NullToolArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig();

    @Inject
    ToolManager toolManager;

    @Test
    public void testNullArgumentValue() {
        toolManager.newTool("nullable")
                .setDescription("Tool with an optional argument")
                .addArgument("note", "Optional note", false, String.class)
                .setHandler(NullToolArgumentTest::describe)
                .register();
        toolManager.newTool("nullable-guarded")
                .setDescription("Tool with an optional argument and an input guardrail")
                .addArgument("note", "Optional note", false, String.class)
                .setInputGuardrails(List.of(PassThroughInputGuardrail.class))
                .setHandler(NullToolArgumentTest::describe)
                .register();

        // A JSON null is a valid argument value; LLM clients often send it for optional parameters
        Map<String, Object> args = new HashMap<>();
        args.put("note", null);

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("nullable", args, toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("present:null", toolResponse.firstContent().asText().text());
                })
                .toolsCall("nullable-guarded", args, toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("present:null", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    private static ToolResponse describe(ToolManager.ToolArguments toolArguments) {
        Map<String, Object> args = toolArguments.args();
        return ToolResponse.success((args.containsKey("note") ? "present:" : "absent:") + args.get("note"));
    }

    public static class PassThroughInputGuardrail implements ToolInputGuardrail {

        @Override
        public void apply(ToolInputContext context) {
            context.setArguments(context.getArguments());
        }

    }

}
