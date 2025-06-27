package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class OptionalListStringInputSchemaTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(1000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testInputSchema() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsList(page -> {
                    assertEquals(2, page.size());
                    assertEquals(
                            "{\"type\":\"object\",\"properties\":{\"tasks\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[]}",
                            page.findByName("runOptionalTasks").inputSchema().toString());
                    assertEquals(
                            "{\"type\":\"object\",\"properties\":{\"tasks\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"tasks\"]}",
                            page.findByName("runTasks").inputSchema().toString());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        public String runTasks(List<String> tasks) {
            return "ok";
        }

        @Tool
        public String runOptionalTasks(Optional<List<String>> tasks) {
            return "ok";
        }

    }

}
