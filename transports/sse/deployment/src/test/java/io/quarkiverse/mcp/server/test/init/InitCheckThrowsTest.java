package io.quarkiverse.mcp.server.test.init;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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

public class InitCheckThrowsTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyInitCheck.class));

    @Test
    public void testInitRequest() throws InterruptedException {
        RestAssured.given()
                .when()
                .headers(Map.of(HttpHeaders.ACCEPT + "", "application/json, text/event-stream"))
                .body(newInitMessage().encode())
                .post(messageEndpoint)
                .then()
                .statusCode(500);
        assertFalse(MyInitCheck.INIT.get());
        assertTrue(MyInitCheck.APPLIED.get());
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
            throw new IllegalStateException();
        }

    }

}
