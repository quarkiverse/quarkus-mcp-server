package io.quarkiverse.mcp.server.test.resources.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.Checks;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResourceTemplatesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTemplates.class, Checks.class));

    @Test
    public void testResourceTemplates() throws URISyntaxException {
        initClient();

        JsonObject resourceTemplatesListMessage = newMessage("resources/templates/list");
        send(resourceTemplatesListMessage);

        JsonObject resourceTemplatesListResponse = waitForLastResponse();

        JsonObject resourceTemplatesListResult = assertResponseMessage(resourceTemplatesListMessage,
                resourceTemplatesListResponse);
        assertNotNull(resourceTemplatesListResult);
        JsonArray resourceTemplates = resourceTemplatesListResult.getJsonArray("resourceTemplates");
        assertEquals(2, resourceTemplates.size());

        assertResourceRead("foo:bar", "file:///bar");
        assertResourceRead("bar:baz", "file:///bar/baz");
    }

    private void assertResourceRead(String expectedText, String uri) {
        JsonObject resourceReadMessage = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));

        send(resourceReadMessage);

        JsonObject resourceReadResponse = waitForLastResponse();

        JsonObject resourceReadResult = assertResponseMessage(resourceReadMessage, resourceReadResponse);
        assertNotNull(resourceReadResult);
        JsonArray contents = resourceReadResult.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject textContent = contents.getJsonObject(0);
        assertEquals(expectedText, textContent.getString("text"));
        assertEquals(uri, textContent.getString("uri"));
    }

}
