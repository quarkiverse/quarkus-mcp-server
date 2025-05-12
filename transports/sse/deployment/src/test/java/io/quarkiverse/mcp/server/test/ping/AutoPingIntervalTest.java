package io.quarkiverse.mcp.server.test.ping;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class AutoPingIntervalTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.auto-ping-interval", "1s");

    @Test
    public void testPing() {
        initClient();
        client().setRequestConsumer(r -> {
            JsonObject pongMessage = Messages.newResult(r.getValue("id"), new JsonObject());
            send(pongMessage);
        });

        List<JsonObject> requests = client().waitForRequests(2);
        JsonObject pingRequest = requests.stream().filter(r -> "ping".equals(r.getString("method"))).findFirst().orElse(null);
        assertNotNull(pingRequest);
    }
}
