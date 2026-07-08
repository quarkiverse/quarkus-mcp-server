package io.quarkiverse.mcp.server.test.cachecontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.CacheScope;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class CacheControlTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(CacheControlResources.class))
            .overrideConfigKey("quarkus.mcp.server.tools.ttl-ms", "60000")
            .overrideConfigKey("quarkus.mcp.server.tools.cache-scope", "public")
            .overrideConfigKey("quarkus.mcp.server.prompts.ttl-ms", "30000")
            .overrideConfigKey("quarkus.mcp.server.prompts.cache-scope", "private")
            .overrideConfigKey("quarkus.mcp.server.resources.ttl-ms", "10000")
            .overrideConfigKey("quarkus.mcp.server.resources.cache-scope", "public")
            .overrideConfigKey("quarkus.mcp.server.resource-templates.ttl-ms", "20000")
            .overrideConfigKey("quarkus.mcp.server.resource-templates.cache-scope", "private");

    @Test
    public void testToolsListCacheControl() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(p -> {
                    assertNotNull(p.cacheControl());
                    assertEquals(60000, p.cacheControl().ttlMs());
                    assertEquals(CacheScope.PUBLIC, p.cacheControl().cacheScope());
                })
                .thenAssertResults();
    }

    @Test
    public void testPromptsListCacheControl() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .promptsList(p -> {
                    assertNotNull(p.cacheControl());
                    assertEquals(30000, p.cacheControl().ttlMs());
                    assertEquals(CacheScope.PRIVATE, p.cacheControl().cacheScope());
                })
                .thenAssertResults();
    }

    @Test
    public void testResourcesListCacheControl() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesList(p -> {
                    assertNotNull(p.cacheControl());
                    assertEquals(10000, p.cacheControl().ttlMs());
                    assertEquals(CacheScope.PUBLIC, p.cacheControl().cacheScope());
                })
                .thenAssertResults();
    }

    @Test
    public void testResourceTemplatesListCacheControl() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesTemplatesList(p -> {
                    assertNotNull(p.cacheControl());
                    assertEquals(20000, p.cacheControl().ttlMs());
                    assertEquals(CacheScope.PRIVATE, p.cacheControl().cacheScope());
                })
                .thenAssertResults();
    }

    @Test
    public void testResourceReadAnnotationCacheControl() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // alpha has @CacheControl(ttlMs = 5000, cacheScope = PRIVATE) - takes precedence over config
                .resourcesRead("file:///cc/alpha", r -> {
                    assertEquals("alpha", r.contents().get(0).asText().text());
                    assertEquals(5000L, r.cacheControl().ttlMs());
                    assertEquals(CacheScope.PRIVATE, r.cacheControl().cacheScope());
                })
                .thenAssertResults();
    }

    @Test
    public void testResourceReadNoAnnotationNoCacheControl() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // bravo has no @CacheControl and no programmatic override - no cache control in response
                .resourcesRead("file:///cc/bravo", r -> {
                    assertEquals("bravo", r.contents().get(0).asText().text());
                    assertNull(r.cacheControl());
                })
                .thenAssertResults();
    }

    @Test
    public void testResourceReadProgrammaticOverride() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // programmaticOverride has @CacheControl(ttlMs=1000) but ResourceResponse sets ttlMs=9999
                .resourcesRead("file:///cc/programmatic_override", r -> {
                    assertEquals("override", r.contents().get(0).asText().text());
                    assertEquals(9999L, r.cacheControl().ttlMs());
                    assertEquals(CacheScope.PRIVATE, r.cacheControl().cacheScope());
                })
                .thenAssertResults();
    }

    @Test
    public void testResourceTemplateReadDefaultScope() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // templateDefaultScope has @CacheControl(ttlMs = 7000) with no explicit cacheScope - defaults to PUBLIC
                .resourcesRead("file:///cc/template_default_scope/1", r -> {
                    assertEquals("template-default-scope-1", r.contents().get(0).asText().text());
                    assertEquals(7000L, r.cacheControl().ttlMs());
                    assertEquals(CacheScope.PUBLIC, r.cacheControl().cacheScope());
                })
                .thenAssertResults();
    }

    @Test
    public void testResourceTemplateReadCacheControl() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // template has @CacheControl(ttlMs = 3000, cacheScope = PUBLIC)
                .resourcesRead("file:///cc/template/42", r -> {
                    assertEquals("template-42", r.contents().get(0).asText().text());
                    assertEquals(3000L, r.cacheControl().ttlMs());
                    assertEquals(CacheScope.PUBLIC, r.cacheControl().cacheScope());
                })
                .thenAssertResults();
    }
}
