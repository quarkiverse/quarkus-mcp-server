package io.quarkiverse.mcp.server.test.resources.icons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.StreamSupport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.Icons;
import io.quarkiverse.mcp.server.IconsProvider;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;

public class ResourceIconsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(1000)
            .withApplicationRoot(
                    root -> root.addClasses(MyResources.class, AlphaIcons.class, BravoIcons.class));

    @Inject
    ResourceManager resourceManager;

    @Test
    public void testIcons() {
        resourceManager.newResource("charlie")
                .setUri("file:///project/charlie")
                .setHandler(
                        args -> new ResourceResponse(List.of(TextResourceContents.create("file:///project/charlie", "ping"))))
                .setIcons(new Icon("file://baz", "image/png"))
                .setDescription("Charlie!")
                .register();

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when().resourcesList(page -> {
            assertEquals(3, page.resources().size());
            ResourceInfo alpha = page.findByUri("file:///project/alpha");
            JsonArray alphaIcons = alpha.icons();
            assertEquals(1, alphaIcons.size());
            assertEquals("file://foo", alphaIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", alphaIcons.getJsonObject(0).getString("mimeType"));

            ResourceInfo bravo = page.findByUri("file:///project/bravo");
            JsonArray bravoIcons = bravo.icons();
            assertEquals(1, bravoIcons.size());
            assertEquals("file://bar", bravoIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", bravoIcons.getJsonObject(0).getString("mimeType"));

            ResourceInfo charlie = page.findByUri("file:///project/charlie");
            JsonArray charlieIcons = charlie.icons();
            assertEquals(1, charlieIcons.size());
            assertEquals("file://baz", charlieIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", charlieIcons.getJsonObject(0).getString("mimeType"));
        }).thenAssertResults();
    }

    public static class MyResources {

        @Icons(AlphaIcons.class)
        @Resource(uri = "file:///project/alpha")
        String alpha() {
            return "ok";
        }

        @Icons(BravoIcons.class)
        @Resource(uri = "file:///project/bravo")
        String bravo() {
            return "ok";
        }

    }

    public static class AlphaIcons implements IconsProvider {

        @Override
        public List<Icon> get(FeatureInfo feature) {
            return List.of(new Icon("file://foo", "image/png"));
        }

    }

    @ApplicationScoped
    public static class BravoIcons implements IconsProvider {

        @Inject
        ResourceManager resourceManager;

        @Override
        public List<Icon> get(FeatureInfo feature) {
            if (StreamSupport.stream(resourceManager.spliterator(), false).anyMatch(r -> r.name().equals(feature.name()))) {
                return List.of(new Icon("file://bar", "image/png"));
            }
            return List.of();
        }

    }

}
