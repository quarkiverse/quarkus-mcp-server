package io.quarkiverse.mcp.server.schema.validator.test.subscribe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class SubscribeSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class));

    @Test
    public void testSubscribe() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // Valid resources/subscribe
                .message(client.newRequest("resources/subscribe")
                        .put("params", new JsonObject().put("uri", "file:///test/alpha")))
                .send()
                // Send a resources/subscribe request without required "params" field
                .message(client.newRequest("resources/subscribe"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                // Send a resources/subscribe request with missing required "uri" field
                .message(client.newRequest("resources/subscribe")
                        .put("params", new JsonObject()))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();
    }

    @Test
    public void testUnsubscribe() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // Valid resources/unsubscribe
                .message(client.newRequest("resources/unsubscribe")
                        .put("params", new JsonObject().put("uri", "file:///test/alpha")))
                .send()
                // Send a resources/unsubscribe request without required "params" field
                .message(client.newRequest("resources/unsubscribe"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                // Send a resources/unsubscribe request with missing required "uri" field
                .message(client.newRequest("resources/unsubscribe")
                        .put("params", new JsonObject()))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();
    }

    public static class MyResources {

        @Resource(uri = "file:///test/alpha")
        ResourceResponse alpha(RequestUri uri) {
            return new ResourceResponse(List.of(new TextResourceContents(uri.value(), "alpha-content", null)));
        }
    }
}
