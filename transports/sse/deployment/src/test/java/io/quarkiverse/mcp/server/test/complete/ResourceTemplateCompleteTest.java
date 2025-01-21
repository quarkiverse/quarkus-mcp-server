package io.quarkiverse.mcp.server.test.complete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
    public void testCompletion() throws URISyntaxException {
        initClient();
        JsonObject completeMessage = newMessage("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/resource")
                                .put("name", "foo_template"))
                        .put("argument", new JsonObject()
                                .put("name", "foo")
                                .put("value", "Ja")));
        send(completeMessage);

        JsonObject completeResponse = waitForLastResponse();

        JsonObject completeResult = assertResponseMessage(completeMessage, completeResponse);
        assertNotNull(completeResult);
        JsonArray values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("Jachym", values.getString(0));

        completeMessage = newMessage("completion/complete")
                .put("params", new JsonObject()
                        .put("ref", new JsonObject()
                                .put("type", "ref/resource")
                                .put("name", "foo_template"))
                        .put("argument", new JsonObject()
                                .put("name", "bar")
                                .put("value", "Ja")));
        send(completeMessage);

        completeResponse = waitForLastResponse();

        completeResult = assertResponseMessage(completeMessage, completeResponse);
        assertNotNull(completeResult);
        values = completeResult.getJsonObject("completion").getJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("_bar", values.getString(0));
    }
}
