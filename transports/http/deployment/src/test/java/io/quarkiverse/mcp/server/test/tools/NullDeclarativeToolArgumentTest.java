package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class NullDeclarativeToolArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class));

    @Test
    public void testNullArgumentValue() {
        // A JSON null is a valid argument value; LLM clients often send it for optional parameters
        Map<String, Object> args = new HashMap<>();
        args.put("note", null);

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("nullable", args, toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("note:null", toolResponse.firstContent().asText().text());
                })
                .toolsCall("optional-nullable", args, toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("optional:false", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String nullable(@ToolArg(required = false) String note) {
            return "note:" + note;
        }

        @Tool(name = "optional-nullable")
        String optionalNullable(Optional<String> note) {
            return "optional:" + note.isPresent();
        }

    }

}
