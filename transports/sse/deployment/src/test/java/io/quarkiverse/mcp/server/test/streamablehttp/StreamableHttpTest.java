package io.quarkiverse.mcp.server.test.streamablehttp;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;

import io.quarkiverse.mcp.server.test.McpServerTest;
import io.restassured.RestAssured;
import io.vertx.core.http.HttpHeaders;

public abstract class StreamableHttpTest extends McpServerTest {

    @BeforeEach
    void init() {
        String testUriStr = testUri.toString();
        if (testUriStr.endsWith("/")) {
            testUriStr = testUriStr.substring(0, testUriStr.length() - 1);
        }
        messageEndpoint = URI.create(testUriStr + sseRootPath());
    }

    @Override
    protected boolean requiresClientInit() {
        return false;
    }

    private final AtomicInteger idGenerator = new AtomicInteger();

    @Override
    protected int nextRequestId() {
        return idGenerator.incrementAndGet();
    }

    @Override
    protected Map<String, Object> defaultHeaders() {
        return Map.of(HttpHeaders.ACCEPT + "", "application/json, text/event-stream");
    }

    protected String initSession() {
        String mcpSessionId = RestAssured.given()
                .when()
                .headers(defaultHeaders())
                .body(newInitMessage().encode())
                .post(messageEndpoint)
                .then()
                .statusCode(200)
                .extract()
                .header(MCP_SESSION_ID_HEADER);
        assertNotNull(mcpSessionId);

        send(newNotification("notifications/initialized"), Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).statusCode(202);

        return mcpSessionId;
    }

}
