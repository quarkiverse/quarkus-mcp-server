package io.quarkiverse.mcp.server.test.resources.templates.icons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.Icons;
import io.quarkiverse.mcp.server.IconsProvider;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceTemplateInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;

public class ResourceTemplateIconsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(1000)
            .withApplicationRoot(
                    root -> root.addClasses(MyResourceTemplates.class, AlphaIcons.class, BravoIcons.class));

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Test
    public void testIcons() {
        resourceTemplateManager.newResourceTemplate("charlie")
                .setUriTemplate("file:///project/charlie/{foo}")
                .setHandler(
                        args -> new ResourceResponse(List.of(TextResourceContents.create("file:///project/charlie", "ping"))))
                .setIcons(new Icon("file://baz", "image/png"))
                .setDescription("Charlie!")
                .register();

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when().resourcesTemplatesList(page -> {
            assertEquals(3, page.templates().size());

            ResourceTemplateInfo alpha = page.findByUriTemplate("file:///project/alpha/{foo}");
            JsonArray alphaIcons = alpha.icons();
            assertEquals(1, alphaIcons.size());
            assertEquals("file://foo", alphaIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", alphaIcons.getJsonObject(0).getString("mimeType"));

            ResourceTemplateInfo bravo = page.findByUriTemplate("file:///project/bravo/{foo}");
            JsonArray bravoIcons = bravo.icons();
            assertEquals(1, bravoIcons.size());
            assertEquals("file://bar", bravoIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", bravoIcons.getJsonObject(0).getString("mimeType"));

            ResourceTemplateInfo charlie = page.findByUriTemplate("file:///project/charlie/{foo}");
            JsonArray charlieIcons = charlie.icons();
            assertEquals(1, charlieIcons.size());
            assertEquals("file://baz", charlieIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", charlieIcons.getJsonObject(0).getString("mimeType"));
        }).thenAssertResults();
    }

    public static class MyResourceTemplates {

        @Icons(AlphaIcons.class)
        @ResourceTemplate(uriTemplate = "file:///project/alpha/{foo}")
        String alpha() {
            return "ok";
        }

        @Icons(BravoIcons.class)
        @ResourceTemplate(uriTemplate = "file:///project/bravo/{foo}")
        String bravo(String foo) {
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
        ResourceTemplateManager resourceTemplateManager;

        @Override
        public List<Icon> get(FeatureInfo feature) {
            if (resourceTemplateManager.getResourceTemplate(feature.name()) != null) {
                return List.of(new Icon("file://bar", "image/png"));
            }
            return List.of();
        }

    }

}
