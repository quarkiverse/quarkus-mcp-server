package io.quarkiverse.mcp.server.test.currentrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;

public class ToolCurrentRequestTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testCurrentRequest() {
        McpSseTestClient client = McpAssured.newSseClient()
                .setAdditionalHeaders(m -> MultiMap.caseInsensitiveMultiMap().add("test-alpha", "bazinga"))
                .build();
        client.connect();

        client.whenBatch()
                .toolsCall("bravo", Map.of("price", 10), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("10bazinga", toolResponse.content().get(0).asText().text());
                })
                .toolsCall("bravo", Map.of("price", 100), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("100bazinga", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Inject
        HttpServerRequest request;

        @Tool
        String bravo(int price) {
            return price + request.getHeader("test-alpha");
        }

    }

}
