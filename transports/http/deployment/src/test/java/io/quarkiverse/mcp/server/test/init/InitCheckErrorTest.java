package io.quarkiverse.mcp.server.test.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

public class InitCheckErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyInitCheck.class));

    @Test
    public void testInitRequest() throws InterruptedException {
        McpStreamableTestClient client = McpAssured.newStreamableClient().build();
        JsonObject request = client.newInitMessage();
        JsonObject response = new JsonObject(RestAssured.given()
                .when()
                .header("Accept", "application/json, text/event-stream")
                .body(request.encode())
                .post(client.mcpEndpoint())
                .then()
                .statusCode(200).extract().body().asString());
        assertFalse(MyInitCheck.INIT.get());
        assertTrue(MyInitCheck.APPLIED.get());
        JsonObject error = response.getJsonObject("error");
        assertNotNull(error);
        assertEquals("Mcp-Test header not set", error.getString("message"));
    }

    @Singleton
    public static class MyInitCheck implements InitialCheck {

        static final AtomicBoolean INIT = new AtomicBoolean();
        static final AtomicBoolean APPLIED = new AtomicBoolean();

        @Inject
        HttpServerRequest request;

        @Notification(Type.INITIALIZED)
        void onInit() {
            INIT.set(true);
        }

        @Override
        public Uni<CheckResult> perform(InitialRequest initialRequest) {
            APPLIED.set(true);
            if (!"foo".equals(request.getHeader("Mcp-Test"))) {
                return InitialCheck.CheckResult.error("Mcp-Test header not set");
            }
            return InitialCheck.CheckResult.successs();
        }

    }

}
