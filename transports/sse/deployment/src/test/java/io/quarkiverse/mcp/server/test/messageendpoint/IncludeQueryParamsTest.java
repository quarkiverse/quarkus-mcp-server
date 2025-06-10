package io.quarkiverse.mcp.server.test.messageendpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class IncludeQueryParamsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.sse.message-endpoint.include-query-params", "true");

    @Override
    protected String ssePath() {
        return new StringBuilder().append("sse?")
                .append("foo=1")
                .append("&bar=2")
                .append("&bar=3")
                .append("&name=")
                .append(URLEncoder.encode("Čenda", StandardCharsets.UTF_8))
                .toString();
    }

    @Test
    public void testQueryParams() {
        initClient();
        assertQuery(messageEndpoint.getQuery());
        JsonObject msg = newToolCallMessage("queryParams");
        send(msg);
        assertToolTextContent(msg);
    }

    private void assertQuery(String query) {
        assertTrue(query.contains("foo=1"));
        assertTrue(query.contains("bar=2"));
        assertTrue(query.contains("bar=3"));
        assertTrue(query.contains("name=Čenda"));
    }

    private void assertToolTextContent(JsonObject msg) {
        JsonObject response = client().waitForResponse(msg);
        JsonObject result = assertResultResponse(msg, response);
        assertNotNull(result);
        assertFalse(result.getBoolean("isError"));
        JsonArray content = result.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        String text4 = textContent.getString("text");
        assertQuery(text4);
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
