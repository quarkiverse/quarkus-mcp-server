package org.acme;

import io.quarkiverse.mcp.server.test.McpAssured;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CheckToolsIT {

    @Test
    public void testToolsCall() {
        try (McpAssured.McpStdioTestClient client = McpAssured.newStdioClient()
                .setStateless()
                .setCommand("java", "-jar", "target/quarkus-mcp-server-1.0.0-SNAPSHOT-runner.jar")
                .build()
                .connect()) {

            client.when()
                    .toolsCall("greetHello", Map.of("value", "Quarkus"), r -> {
                        assertFalse(r.isError());
                        assertEquals(true, r.firstContent().asText().text().startsWith("Hello from the MCP server"));
                    })
                    .thenAssertResults();

            client.when()
                    .toolsCall("greetBye", Map.of("value", "Quarkus"), r -> {
                        assertFalse(r.isError());
                        assertEquals(true, r.firstContent().asText().text().startsWith("Bye from the MCP server"));
                    })
                    .thenAssertResults();
        }
    }
}
