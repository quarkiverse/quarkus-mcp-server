package io.quarkiverse.mcp.server.test.messageendpoint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

public class IncludeQueryParamsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.sse.message-endpoint.include-query-params", "true");

    @Test
    public void testQueryParams() {
        McpSseTestClient client = McpAssured.newSseClient()
                .setSsePath(new StringBuilder().append("/mcp/sse?")
                        .append("foo=1")
                        .append("&bar=2")
                        .append("&bar=3")
                        .append("&name=")
                        .append(URLEncoder.encode("Čenda", StandardCharsets.UTF_8))
                        .toString())
                .build()
                .connect();

        assertQuery(client.messageEndpoint().getQuery());

        client.when()
                .toolsCall("queryParams", toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertQuery(toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    private void assertQuery(String query) {
        assertTrue(query.contains("foo=1"));
        assertTrue(query.contains("bar=2"));
        assertTrue(query.contains("bar=3"));
        assertTrue(query.contains("name=Čenda"));
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
