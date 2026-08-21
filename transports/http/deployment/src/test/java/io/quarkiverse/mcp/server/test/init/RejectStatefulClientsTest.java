package io.quarkiverse.mcp.server.test.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

/**
 * Verifies the "reject stateful clients" recipe recommended for serverless / non-sticky load balancer deployments:
 * an {@link InitialCheck} that rejects any client using a stateful protocol version, forcing clients to use the
 * stateless protocol (which requires no session affinity).
 *
 * @see RejectStatefulClients
 */
public class RejectStatefulClientsTest extends McpServerTest {

    static final String REJECTION_MESSAGE = "This server only accepts stateless clients (protocol version 2026-07-28 or later)";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(RejectStatefulClients.class, MyTools.class));

    @Test
    public void testStatefulClientRejected() {
        // A stateful client performs the initialize handshake; the InitialCheck runs and rejects it.
        // The rejection is a spec-compliant JSON-RPC error (HTTP 200 with an error object in the body).
        McpStreamableTestClient client = McpAssured.newStreamableClient().build();
        JsonObject request = client.newInitMessage();
        JsonObject response = new JsonObject(RestAssured.given()
                .when()
                .header("Accept", "application/json, text/event-stream")
                .body(request.encode())
                .post(client.mcpEndpoint())
                .then()
                .statusCode(200).extract().body().asString());
        JsonObject error = response.getJsonObject("error");
        assertNotNull(error, "A stateful client must receive a JSON-RPC error");
        assertEquals(JsonRpcErrorCodes.INTERNAL_ERROR, error.getInteger("code"));
        assertEquals(REJECTION_MESSAGE, error.getString("message"));
    }

    @Test
    public void testStatelessClientAccepted() {
        // A stateless client does not perform the initialize handshake, so InitialChecks are not applied and
        // the client can use the server normally.
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsCall("echo", Map.of("message", "hello stateless"), r -> {
                    assertFalse(r.isError());
                    assertEquals("hello stateless", r.firstContent().asText().text());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Singleton
    public static class RejectStatefulClients implements InitialCheck {

        @Override
        public Uni<CheckResult> perform(InitialRequest initialRequest) {
            if (!initialRequest.protocolVersion().isStateless()) {
                return CheckResult.error(REJECTION_MESSAGE);
            }
            return CheckResult.success();
        }
    }

    public static class MyTools {

        @Tool
        String echo(String message) {
            return message;
        }
    }

}
