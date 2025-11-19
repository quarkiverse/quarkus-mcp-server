package io.quarkiverse.mcp.server.test.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class ResourceJsonTextContentEncoderTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class, MyObject.class));

    @Test
    public void testEncoder() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .resourcesRead("file:///bravo", r -> {
                    assertEquals("{\"name\":\"foo\",\"sum\":2,\"valid\":true}", r.contents().get(0).asText().text());
                })
                .resourcesRead("file:///1", r -> {
                    assertEquals("{\"name\":\"foo\",\"sum\":3,\"valid\":true}", r.contents().get(0).asText().text());
                })
                .resourcesRead("file:///list_bravo", r -> {
                    assertEquals("{\"name\":\"foo\",\"sum\":4,\"valid\":true}", r.contents().get(0).asText().text());
                })
                .resourcesRead("file:///uni_list_bravo", r -> {
                    assertEquals("{\"name\":\"foo\",\"sum\":5,\"valid\":true}", r.contents().get(0).asText().text());
                })
                .thenAssertResults();
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
