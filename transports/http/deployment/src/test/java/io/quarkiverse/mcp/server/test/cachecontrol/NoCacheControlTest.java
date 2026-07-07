package io.quarkiverse.mcp.server.test.cachecontrol;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class NoCacheControlTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(NoCacheControlResources.class));

    @Test
    public void testNoCacheControlOnList() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(p -> assertNull(p.cacheControl()))
                .promptsList(p -> assertNull(p.cacheControl()))
                .resourcesList(p -> assertNull(p.cacheControl()))
                .resourcesTemplatesList(p -> assertNull(p.cacheControl()))
                .thenAssertResults();
    }

    @Test
    public void testNoCacheControlOnResourceRead() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesRead("file:///nocc/alpha", r -> {
                    assertNull(r.cacheControl());
                })
                .thenAssertResults();
    }
}
