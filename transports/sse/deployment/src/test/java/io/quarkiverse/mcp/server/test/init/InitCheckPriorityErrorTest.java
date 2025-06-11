package io.quarkiverse.mcp.server.test.init;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkiverse.mcp.server.test.StreamableHttpTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

public class InitCheckPriorityErrorTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(AlphaCheck.class, BravoCheck.class));

    @Test
    public void testInitRequest() throws InterruptedException {
        JsonObject request = newInitMessage();
        JsonObject response = new JsonObject(RestAssured.given()
                .when()
                .headers(Map.of(HttpHeaders.ACCEPT + "", "application/json, text/event-stream"))
                .body(request.encode())
                .post(messageEndpoint)
                .then()
                .statusCode(200).extract().body().asString());
        assertFalse(AlphaCheck.INIT.get());
        assertTrue(AlphaCheck.APPLIED.get());
        assertTrue(BravoCheck.APPLIED.get());
        assertErrorMessage(request, response, "Mcp-Test header not set");
    }

    @Singleton
    public static class AlphaCheck implements InitialCheck {

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

    @Priority(5) // this check should be applied first
    @Singleton
    public static class BravoCheck implements InitialCheck {

        static final AtomicBoolean APPLIED = new AtomicBoolean();

        @Override
        public Uni<CheckResult> perform(InitialRequest initialRequest) {
            APPLIED.set(true);
            return InitialCheck.CheckResult.successs();
        }

    }

}
