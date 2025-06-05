package io.quarkiverse.mcp.server.test.streamablehttp;

import static io.quarkiverse.mcp.server.sse.runtime.StreamableHttpMcpMessageHandler.MCP_SESSION_ID_HEADER;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.StreamableHttpTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;

public class TerminateSessionTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testTerminateSession() {
        String mcpSessionId = initSession();

        RestAssured.given()
                .when()
                .headers(Map.of(MCP_SESSION_ID_HEADER, mcpSessionId))
                .delete(messageEndpoint)
                .then();

        JsonObject toolListMessage = newMessage("tools/list");
        send(toolListMessage, Map.of(MCP_SESSION_ID_HEADER, mcpSessionId)).statusCode(404);

    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "" + price * 42;
        }
    }

}
