package io.quarkiverse.mcp.server.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateCompletionManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResourceTemplateProgrammaticCompleteTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResourceTemplates.class));

    static final List<String> NAMES = List.of("Martin", "Lu", "Jachym", "Vojtik", "Onda");

    @Inject
    ResourceTemplateCompletionManager manager;

    @Test
    public void testCompletion() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        manager.newCompletion("foo_template")
                .setArgumentName("foo")
                .setHandler(args -> CompletionResponse.create(
                        NAMES.stream().filter(n -> n.startsWith(args.argumentValue())).toList()))
                .register();

        manager.newCompletion("foo_template")
                .setArgumentName("bar")
                .setHandler(args -> CompletionResponse.create(List.of("_bar")))
                .register();

        assertThrows(IllegalStateException.class, () -> manager.newCompletion("foo_nonexistent")
                .setArgumentName("bar")
                .setHandler(args -> CompletionResponse.create(List.of()))
                .register());

        JsonObject completeMessage = client.newRequest("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/resource")
                                .put("name", "foo_template"))
                        .put("argument", new JsonObject()
                                .put("name", "foo")
                                .put("value", "Ja")));
        client.sendAndForget(completeMessage);

        JsonObject completeResponse = client.waitForResponse(completeMessage);

        JsonObject completeResult = completeResponse.getJsonObject("result");
        assertNotNull(completeResult);
        JsonArray values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("Jachym", values.getString(0));

        completeMessage = client.newRequest("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/resource")
                                .put("name", "foo_template"))
                        .put("argument", new JsonObject()
                                .put("name", "bar")
                                .put("value", "Ja")));
        client.sendAndForget(completeMessage);

        completeResponse = client.waitForResponse(completeMessage);

        completeResult = completeResponse.getJsonObject("result");
        assertNotNull(completeResult);
        values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("_bar", values.getString(0));
    }

    public static class MyResourceTemplates {

        @ResourceTemplate(uriTemplate = "file:///{foo}/{bar}")
        TextResourceContents foo_template(String foo, String bar, RequestUri uri) {
            return TextResourceContents.create(uri.value(), foo + ":" + bar);
        }

    }

}
