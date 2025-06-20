package io.quarkiverse.mcp.server.test.messageendpoint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.http.HttpServerRequest;

public class QueryParamsNotIncludedByDefaultTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testQueryParams() {
        McpSseTestClient client = McpAssured.newSseClient()
                .setSsePath(new StringBuilder().append("/mcp/sse?")
                        .append("foo=1")
                        .append("&bar=2")
                        .toString())
                .build()
                .connect();

        client.when()
                .toolsCall("queryParams", r -> {
                    String text4 = r.content().get(0).asText().text();
                    assertFalse(text4.contains("foo=1"));
                    assertFalse(text4.contains("bar=2"));
                    // HttpServerRequest#params() also contains path params
                    assertTrue(text4.contains("id="));
                })
                .thenAssertResults();

        assertNull(client.messageEndpoint().getQuery());
    }

    public static class MyTools {

        @Inject
        HttpServerRequest request;

        @Tool
        String queryParams() {
            return request.params().entries().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(","));
        }

    }
}
