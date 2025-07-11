package io.quarkiverse.mcp.server.test.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;

public class ToolBatchTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testBatchMessage() {
        McpSseTestClient client = McpAssured
                .newSseClient()
                .setAdditionalHeaders(m -> MultiMap.caseInsensitiveMultiMap().add("test-foo", "bar"))
                .build()
                .connect();

        Set<String> fooIds = new HashSet<>();
        client.whenBatch()
                .toolsCall("bravo", Map.of("price", 10), toolResponse -> {
                    String text = toolResponse.content().get(0).asText().text();
                    assertEquals("420", text.substring(0, text.indexOf("::")));
                    fooIds.add(text.substring(text.indexOf("::") + 2));
                })
                .toolsCall("bravo", Map.of("price", 100), toolResponse -> {
                    String text = toolResponse.content().get(0).asText().text();
                    assertEquals("4200", text.substring(0, text.indexOf("::")));
                    fooIds.add(text.substring(text.indexOf("::") + 2));
                })
                .thenAssertResults();
        assertEquals(2, fooIds.size());
    }

    public static class MyTools {

        @Inject
        Foo foo;

        @Inject
        HttpServerRequest request;

        @Tool
        String bravo(int price) {
            if (!"bar".equals(request.getHeader("test-foo"))) {
                throw new IllegalStateException();
            }
            return "" + price * 42 + "::" + foo.getId();
        }

    }

    @RequestScoped
    public static class Foo {

        private String id;

        @PostConstruct
        void init() {
            this.id = UUID.randomUUID().toString();
        }

        public String getId() {
            return id;
        }

    }

}
