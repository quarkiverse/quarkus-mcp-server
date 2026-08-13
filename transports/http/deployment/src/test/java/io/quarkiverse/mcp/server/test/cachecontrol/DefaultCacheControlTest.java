package io.quarkiverse.mcp.server.test.cachecontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CacheControl;
import io.quarkiverse.mcp.server.CacheScope;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Without any configured cache control, the {@code CacheableResult} fields are omitted for
 * pre-2026-07-28 protocol versions but emitted with spec-valid defaults (immediately stale, public) as of 2026-07-28,
 * where they are required.
 */
public class DefaultCacheControlTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(Features.class));

    @Test
    public void testListsDefaultStateful() {
        // Default client negotiates 2025-11-25 (stateful) - fields are not part of the schema and must be omitted
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(p -> assertNull(p.cacheControl()))
                .promptsList(p -> assertNull(p.cacheControl()))
                .resourcesList(p -> assertNull(p.cacheControl()))
                .resourcesTemplatesList(p -> assertNull(p.cacheControl()))
                .thenAssertResults();
    }

    @Test
    public void testListsDefaultStateless() {
        // Stateless client negotiates 2026-07-28 - fields are required and default to (0, public)
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();
        client.when()
                .toolsList(p -> assertDefault(p.cacheControl()))
                .promptsList(p -> assertDefault(p.cacheControl()))
                .resourcesList(p -> assertDefault(p.cacheControl()))
                .resourcesTemplatesList(p -> assertDefault(p.cacheControl()))
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testResourceReadDefaultStateless() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();
        client.when()
                .resourcesRead("file:///default/res", r -> {
                    assertEquals("res", r.contents().get(0).asText().text());
                    assertDefault(r.cacheControl());
                })
                .resourcesRead("file:///default/tmpl/1", r -> {
                    assertEquals("tmpl-1", r.contents().get(0).asText().text());
                    assertDefault(r.cacheControl());
                })
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testDiscoverDefaultCacheControl() {
        McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect(initResult -> {
                    // server/discover is a 2026-07-28+ method - ttlMs/cacheScope default to (0, public)
                    assertDefault(initResult.cacheControl());
                })
                .disconnect();
    }

    private static void assertDefault(CacheControl cacheControl) {
        assertNotNull(cacheControl);
        assertEquals(0L, cacheControl.ttlMs());
        assertEquals(CacheScope.PUBLIC, cacheControl.cacheScope());
    }

    public static class Features {

        @Resource(uri = "file:///default/res")
        ResourceResponse res(RequestUri uri) {
            return new ResourceResponse(List.of(new TextResourceContents(uri.value(), "res", null)));
        }

        @ResourceTemplate(uriTemplate = "file:///default/tmpl/{id}")
        TextResourceContents tmpl(String id, RequestUri uri) {
            return new TextResourceContents(uri.value(), "tmpl-" + id, null);
        }

        @Tool
        String echo(String message) {
            return message;
        }

        @Prompt
        PromptResponse prompt() {
            return new PromptResponse("default", List.of(new PromptMessage(Role.USER, new TextContent("hi"))));
        }
    }
}
