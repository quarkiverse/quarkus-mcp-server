package io.quarkiverse.mcp.server.test.ping;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class PingTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication();

    @Test
    public void testPing() throws URISyntaxException {
        initClient();
        JsonObject pingMessage = newMessage("ping");
        send(pingMessage);

        JsonObject pingResponse = waitForLastResponse();

        JsonObject pingResult = assertResponseMessage(pingMessage, pingResponse);
        assertNotNull(pingResult);
        assertTrue(pingResult.isEmpty());
    }
}
