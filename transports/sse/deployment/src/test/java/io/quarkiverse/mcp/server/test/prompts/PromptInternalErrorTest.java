package io.quarkiverse.mcp.server.test.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class PromptInternalErrorTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    @Test
    public void testError() {
        initClient();
        JsonObject message = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", "uni_bar")
                        .put("arguments", new JsonObject().put("val", "lav")));
        send(message);
        JsonObject response = waitForLastResponse();
        assertEquals(JsonRPC.INTERNAL_ERROR, response.getJsonObject("error").getInteger("code"));
        assertEquals("Internal error", response.getJsonObject("error").getString("message"));
    }

    public static class MyPrompts {

        @Prompt
        Uni<PromptMessage> uni_bar(String val) {
            return Uni.createFrom().failure(new NullPointerException());
        }

    }

}
