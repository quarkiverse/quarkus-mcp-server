package io.quarkiverse.mcp.server.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URISyntaxException;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptCompletionManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PromptProgrammaticCompleteTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class));

    static final List<String> NAMES = List.of("Martin", "Lu", "Jachym", "Vojtik", "Onda");

    @Inject
    PromptCompletionManager manager;

    @Test
    public void testCompletion() throws URISyntaxException {
        initClient();

        manager.newCompletion("foo")
                .setArgumentName("name")
                .setHandler(args -> new CompletionResponse(
                        NAMES.stream().filter(n -> n.startsWith(args.argumentValue())).toList(), 10, false))
                .register();

        manager.newCompletion("foo")
                .setArgumentName("suffix")
                .setHandler(args -> new CompletionResponse(List.of("_foo"), 1, false))
                .register();

        assertThrows(IllegalStateException.class, () -> manager.newCompletion("foo")
                .setArgumentName("nonexistent")
                .setHandler(args -> new CompletionResponse(
                        List.of(), 0, false))
                .register());

        JsonObject completeMessage = newMessage("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/prompt")
                                .put("name", "foo"))
                        .put("argument", new JsonObject()
                                .put("name", "name")
                                .put("value", "Vo")));
        send(completeMessage);

        JsonObject completeResponse = waitForLastResponse();

        JsonObject completeResult = assertResponseMessage(completeMessage, completeResponse);
        assertNotNull(completeResult);
        JsonArray values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("Vojtik", values.getString(0));

        completeMessage = newMessage("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/prompt")
                                .put("name", "foo"))
                        .put("argument", new JsonObject()
                                .put("name", "suffix")
                                .put("value", "Vo")));
        send(completeMessage);

        completeResponse = waitForLastResponse();

        completeResult = assertResponseMessage(completeMessage, completeResponse);
        assertNotNull(completeResult);
        values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("_foo", values.getString(0));

        assertThrows(IllegalArgumentException.class,
                () -> manager.newCompletion("foo").setArgumentName("suffix").setHandler(args -> null).register());
    }

    public static class MyPrompts {

        @Prompt(description = "Not much we can say here.")
        PromptMessage foo(@PromptArg(description = "The name") String name, String suffix) {
            return new PromptMessage("user", new TextContent(name.toLowerCase() + suffix));
        }

    }
}
