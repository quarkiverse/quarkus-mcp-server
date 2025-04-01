package io.quarkiverse.mcp.server.test.initrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class InitialRequestTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testInitRequest() throws URISyntaxException {
        initClient();

        JsonObject toolCallMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "testInitRequest"));
        send(toolCallMessage);

        JsonObject toolCallResponse = waitForLastResponse();

        JsonObject toolCallResult = assertResponseMessage(toolCallMessage, toolCallResponse);
        assertNotNull(toolCallResult);
        assertFalse(toolCallResult.getBoolean("isError"));
        JsonArray content = toolCallResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("ok", textContent.getString("text"));
    }

    public static class MyTools {

        @Tool
        String testInitRequest(McpConnection connection) {
            InitialRequest initRequest = connection.initialRequest();
            if (initRequest != null) {
                if (!initRequest.protocolVersion().equals("2024-11-05")) {
                    throw new IllegalStateException();
                }
                if (!initRequest.implementation().name().equals("test-client")) {
                    throw new IllegalStateException();
                }
                if (initRequest.clientCapabilities().size() != 1
                        || !initRequest.clientCapabilities().get(0).name().equals("sampling")) {
                    throw new IllegalStateException();
                }
            }
            return "ok";
        }
    }

    @Override
    protected JsonObject getClientCapabilities() {
        return new JsonObject().put("sampling", Map.of());
    }

}
