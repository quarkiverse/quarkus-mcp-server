package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ResourceJsonTextContentEncoderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class, MyObject.class));

    @Test
    public void testEncoder() {
        initClient();
        assertResourceReadResponse("file:///bravo", 2);
        assertResourceReadResponse("file:///1", 3);
        assertResourceReadResponse("file:///list_bravo", 4);
        assertResourceReadResponse("file:///uni_list_bravo", 5);
    }

    private void assertResourceReadResponse(String uri, int expectedSum) {
        JsonObject message = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));
        send(message);
        JsonObject resourceResponse = waitForLastResponse();
        JsonObject resourceResult = assertResponseMessage(message, resourceResponse);
        assertNotNull(resourceResult);
        JsonArray contents = resourceResult.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject textContent = contents.getJsonObject(0);
        assertEquals(uri, textContent.getString("uri"));
        // Note that quotation marks in the original message must be escaped
        assertEquals("{\"name\":\"foo\",\"sum\":" + expectedSum + ",\"valid\":true}", textContent.getString("text"));
    }

    public record MyObject(String name, int sum, boolean valid) {

    }

    public static class MyResources {

        @Resource(uri = "file:///bravo")
        MyObject bravo() {
            return new MyObject("foo", 2, true);
        }

        @ResourceTemplate(uriTemplate = "file:///{price}")
        Uni<MyObject> uni_bravo(String price) {
            return Uni.createFrom().item(new MyObject("foo", Integer.parseInt(price) * 3, true));
        }

        @Resource(uri = "file:///list_bravo")
        List<MyObject> list_bravo() {
            return List.of(new MyObject("foo", 4, true));
        }

        @Resource(uri = "file:///uni_list_bravo")
        Uni<List<MyObject>> uni_list_bravo() {
            return Uni.createFrom().item(List.of(new MyObject("foo", 5, true)));
        }

    }

}
