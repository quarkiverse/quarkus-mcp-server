package io.quarkiverse.mcp.server.cli.adapter.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;

public class ServerFeaturesIT {

    @Test
    public void testTool() {
        try (McpStdioTestClient client = McpAssured.newStdioClient()
                .setCommand("java", "-jar",
                        Paths.get(System.getProperty("user.dir")).toAbsolutePath()
                                .resolve("target/quarkus-app/quarkus-run.jar").toString(),
                        "--mcp")
                .build()
                .connect()) {

            client.when()
                    .toolsList(page -> {
                        assertEquals(1, page.size());
                        var tool = page.findByName("codeservicecommand");
                        assertNotNull(tool);
                        assertNotNull(tool.inputSchema());
                        var properties = tool.inputSchema().getJsonObject("properties");
                        assertEquals(2, properties.size());
                        assertNotNull(properties.getJsonObject("language"));
                        assertEquals("string", properties.getJsonObject("language").getString("type"));
                        // Verify the @Option parameter name derives from the longest CLI option name,
                        // not the Java field name ("codeStyle") or the short form ("s")
                        assertNotNull(properties.getJsonObject("style"),
                                "Option parameter should be named 'style' (from --style), not 'codeStyle' (field name)");
                        assertEquals("string", properties.getJsonObject("style").getString("type"));
                    })
                    .toolsCall("codeservicecommand", Map.of("language", "java", "style", "plain"), response -> {
                        assertFalse(response.isError());
                        assertEquals(1, response.content().size());
                        assertEquals("System.out.println(\"Hello world!\");",
                                response.content().get(0).asText().text().strip());
                    })
                    .thenAssertResults();
        }
    }

}
