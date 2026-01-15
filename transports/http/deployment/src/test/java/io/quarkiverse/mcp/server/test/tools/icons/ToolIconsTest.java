package io.quarkiverse.mcp.server.test.tools.icons;

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
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;

public class ToolIconsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(1000)
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, AlphaIcons.class, BravoIcons.class));

    @Inject
    ToolManager toolManager;

    @Test
    public void testIcons() {
        toolManager.newTool("charlie")
                .setHandler(args -> ToolResponse.success("ok"))
                .setIcons(new Icon("file://baz", "image/png"))
                .setDescription("Charlie!")
                .register();

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when().toolsList(page -> {
            assertEquals(3, page.tools().size());

            ToolInfo alphaTool = page.findByName("alpha");
            JsonArray alphaIcons = alphaTool.icons();
            assertEquals(1, alphaIcons.size());
            assertEquals("file://foo", alphaIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", alphaIcons.getJsonObject(0).getString("mimeType"));

            ToolInfo bravoTool = page.findByName("bravo");
            JsonArray bravoIcons = bravoTool.icons();
            assertEquals(1, bravoIcons.size());
            assertEquals("file://bar", bravoIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", bravoIcons.getJsonObject(0).getString("mimeType"));

            ToolInfo charlie = page.findByName("charlie");
            JsonArray charlieIcons = charlie.icons();
            assertEquals(1, charlieIcons.size());
            assertEquals("file://baz", charlieIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", charlieIcons.getJsonObject(0).getString("mimeType"));
        }).thenAssertResults();
    }

    public static class MyTools {

        @Icons(AlphaIcons.class)
        @Tool
        String alpha() {
            return "ok";
        }

        @Icons(BravoIcons.class)
        @Tool
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
        ToolManager toolManager;

        @Override
        public List<Icon> get(FeatureInfo feature) {
            if (toolManager.getTool(feature.name()) != null) {
                return List.of(new Icon("file://bar", "image/png"));
            }
            return List.of();
        }

    }

}
