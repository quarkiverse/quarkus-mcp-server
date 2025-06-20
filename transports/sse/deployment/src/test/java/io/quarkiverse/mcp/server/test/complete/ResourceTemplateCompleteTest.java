package io.quarkiverse.mcp.server.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResourceTemplateCompleteTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResourceTemplates.class));

    @Test
    public void testCompletion() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        JsonObject completeMessage = client.newMessage("completion/complete")
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

        completeMessage = client.newMessage("completion/complete")
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
}
