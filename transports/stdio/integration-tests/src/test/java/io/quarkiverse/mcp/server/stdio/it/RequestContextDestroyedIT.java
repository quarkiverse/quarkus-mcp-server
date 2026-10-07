package io.quarkiverse.mcp.server.stdio.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;

public class RequestContextDestroyedIT {

    static final int CALL_COUNT = 3;

    @Test
    public void testRequestContextDestroyed() throws InterruptedException {
        try (McpStdioTestClient client = McpAssured.newConnectedStdioClient()) {
            // --- tools: three execution models, CALL_COUNT times each ---
            for (int i = 0; i < CALL_COUNT; i++) {
                client.when()
                        .toolsCall("ping_worker", r -> assertEquals("pong", r.firstContent().asText().text()))
                        .toolsCall("ping_virtual", r -> assertEquals("pong", r.firstContent().asText().text()))
                        .toolsCall("ping_reactive", r -> assertEquals("pong", r.firstContent().asText().text()))
                        .thenAssertResults();
            }

            // --- tool: error path (thrown exception) ---
            client.when()
                    .toolsCall("ping_error", r -> assertTrue(r.isError(),
                            "Expected isError=true for a throwing tool"))
                    .thenAssertResults();

            // --- prompt: request context must be destroyed after prompts/get ---
            client.when()
                    .promptsGet("ping_prompt", r -> assertEquals("pong",
                            r.messages().get(0).content().asText().text()))
                    .thenAssertResults();

            // --- resource: request context must be destroyed after resources/read ---
            client.when()
                    .resourcesRead("file:///ping", r -> assertEquals("pong",
                            r.contents().get(0).asText().text()))
                    .thenAssertResults();

            // The request context of each call - and thus its request scoped bean - must be destroyed
            // once the call is done. Destruction happens in contextEnd(), after the response is sent,
            // so poll the report until the counts settle (or fail after a timeout).
            String expected = "worker=" + CALL_COUNT
                    + ",virtual=" + CALL_COUNT
                    + ",reactive=" + CALL_COUNT
                    + ",error=1"
                    + ",prompt=1"
                    + ",resource=1";
            long deadline = System.currentTimeMillis() + 10_000;
            String report = null;
            do {
                AtomicReference<String> ref = new AtomicReference<>();
                client.when()
                        .toolsCall("destroyed_report", r -> ref.set(r.firstContent().asText().text()))
                        .thenAssertResults();
                report = ref.get();
                if (expected.equals(report)) {
                    return;
                }
                Thread.sleep(200);
            } while (System.currentTimeMillis() < deadline);

            fail("Request scoped beans were not all destroyed, last report: " + report);
        }
    }
}
