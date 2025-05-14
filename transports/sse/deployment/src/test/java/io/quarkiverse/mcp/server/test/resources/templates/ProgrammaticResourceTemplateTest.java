package io.quarkiverse.mcp.server.test.resources.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ProgrammaticResourceTemplateTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTemplates.class));

    @Inject
    MyTemplates myTemplates;

    @Test
    public void testResources() {
        initClient();
        assertResources(0);
        assertResourceReadResponseError("file:///alpha/nok");

        myTemplates.register("alpha");
        assertThrows(IllegalArgumentException.class, () -> myTemplates.register("alpha"));
        assertThrows(NullPointerException.class, () -> myTemplates.register(null));

        assertResources(0);
        assertResourceReadResponse("file:///alpha/ok", "ok");

        myTemplates.register("bravo");

        assertResources(0);
        assertResourceReadResponse("file:///bravo/bim", "bim");

        myTemplates.remove("alpha");
        assertResources(0);
        assertResourceReadResponseError("file:///alpha/nok");
        assertResourceReadResponse("file:///bravo/3", "3");
    }

    private void assertResources(int expectedSize) {
        JsonObject resourcesListMessage = newMessage("resources/list");
        send(resourcesListMessage);

        JsonObject resourcesListResponse = waitForLastResponse();

        JsonObject resourcesListResult = assertResultResponse(resourcesListMessage, resourcesListResponse);
        assertNotNull(resourcesListResult);
        JsonArray resources = resourcesListResult.getJsonArray("resources");
        assertEquals(expectedSize, resources.size());
    }

    private void assertResourceReadResponseError(String uri) {
        JsonObject message = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));
        send(message);
        JsonObject response = waitForLastResponse();
        assertEquals(JsonRPC.RESOURCE_NOT_FOUND, response.getJsonObject("error").getInteger("code"));
        assertEquals("Invalid resource uri: " + uri, response.getJsonObject("error").getString("message"));

    }

    private void assertResourceReadResponse(String uri, String expectedText) {
        JsonObject message = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));
        send(message);
        JsonObject resourceResponse = waitForLastResponse();
        JsonObject resourceResult = assertResultResponse(message, resourceResponse);
        assertNotNull(resourceResult);
        JsonArray contents = resourceResult.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject textContent = contents.getJsonObject(0);
        assertEquals(uri, textContent.getString("uri"));
        assertEquals(expectedText, textContent.getString("text"));
    }

    @Singleton
    public static class MyTemplates {

        @Inject
        ResourceTemplateManager manager;

        void register(String name) {
            manager.newResourceTemplate(name)
                    .setUriTemplate("file:///" + name + "/{foo}")
                    .setDescription(name + " description!")
                    .setHandler(
                            args -> new ResourceResponse(
                                    List.of(TextResourceContents.create(args.requestUri().value(), args.args().get("foo")))))
                    .register();
        }

        ResourceTemplateManager.ResourceTemplateInfo remove(String name) {
            return manager.removeResourceTemplate(name);
        }

    }

}
