package io.quarkiverse.mcp.server.test.resources.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResourceTemplatesPaginationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.mcp.server.resource-templates.page-size", "3");

    @Inject
    ResourceTemplateManager manager;

    @Test
    public void testResources() {
        int loop = 8;
        for (int i = 1; i <= loop; i++) {
            String name = i + "";
            manager.newResourceTemplate(name)
                    .setUriTemplate("file:///" + name)
                    .setDescription(name)
                    .setHandler(
                            args -> new ResourceResponse(
                                    List.of(TextResourceContents.create(args.requestUri().value(), "Result: " + name))))
                    .register();
        }

        initClient();

        JsonObject message = newMessage("resources/templates/list");
        send(message);

        JsonObject response = waitForLastResponse();
        JsonObject result = assertResultResponse(message, response);
        assertNotNull(result);
        JsonArray resources = result.getJsonArray("resourceTemplates");
        assertEquals(3, resources.size());
        String cursor = result.getString("nextCursor");
        assertNotNull(cursor);

        assertResourceTemplate(resources.getJsonObject(0), "1", "file:///1");
        assertResourceTemplate(resources.getJsonObject(1), "2", "file:///2");
        assertResourceTemplate(resources.getJsonObject(2), "3", "file:///3");

        message = newMessage("resources/templates/list").put("params", new JsonObject().put("cursor", cursor));
        send(message);

        response = waitForLastResponse();
        result = assertResultResponse(message, response);
        assertNotNull(result);
        resources = result.getJsonArray("resourceTemplates");
        assertEquals(3, resources.size());
        cursor = result.getString("nextCursor");
        assertNotNull(cursor);

        assertResourceTemplate(resources.getJsonObject(0), "4", "file:///4");
        assertResourceTemplate(resources.getJsonObject(1), "5", "file:///5");
        assertResourceTemplate(resources.getJsonObject(2), "6", "file:///6");

        message = newMessage("resources/templates/list").put("params", new JsonObject().put("cursor", cursor));
        send(message);

        response = waitForLastResponse();
        result = assertResultResponse(message, response);
        assertNotNull(result);
        resources = result.getJsonArray("resourceTemplates");
        assertEquals(2, resources.size());
        assertNull(result.getString("nextCursor"));

        assertResourceTemplate(resources.getJsonObject(0), "7", "file:///7");
        assertResourceTemplate(resources.getJsonObject(1), "8", "file:///8");
    }

    private void assertResourceTemplate(JsonObject resource, String name, String uri) {
        assertEquals(name, resource.getString("name"));
        assertEquals(name, resource.getString("description"));
        assertEquals(uri, resource.getString("uriTemplate"));
    }

}
