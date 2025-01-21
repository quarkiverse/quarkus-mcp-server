package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkiverse.mcp.server.test.Options;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class InvalidToolNameTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(FooService.class, Options.class, Checks.class, MyTools.class));

    @Test
    public void testError() throws URISyntaxException {
        initClient();
        JsonObject message = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "nonexistent")
                        .put("arguments", new JsonObject()));
        send(message);
        JsonObject response = waitForLastResponse();
        assertEquals(JsonRPC.INVALID_PARAMS, response.getJsonObject("error").getInteger("code"));
        assertEquals("Invalid tool name: nonexistent", response.getJsonObject("error").getString("message"));
    }

}
