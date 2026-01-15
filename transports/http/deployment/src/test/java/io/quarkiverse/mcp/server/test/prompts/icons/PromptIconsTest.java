package io.quarkiverse.mcp.server.test.prompts.icons;

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
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.PromptInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonArray;

public class PromptIconsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(1000)
            .withApplicationRoot(
                    root -> root.addClasses(MyPrompts.class, AlphaIcons.class, BravoIcons.class));

    @Inject
    PromptManager promptManager;

    @Test
    public void testIcons() {
        promptManager.newPrompt("charlie")
                .setHandler(args -> PromptResponse.withMessages(PromptMessage.withUserRole("ok")))
                .setIcons(new Icon("file://baz", "image/png"))
                .setDescription("Charlie!")
                .register();

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when().promptsList(page -> {
            assertEquals(3, page.prompts().size());
            PromptInfo alpha = page.findByName("alpha");
            JsonArray alphaIcons = alpha.icons();
            assertEquals(1, alphaIcons.size());
            assertEquals("file://foo", alphaIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", alphaIcons.getJsonObject(0).getString("mimeType"));

            PromptInfo bravo = page.findByName("bravo");
            JsonArray bravoIcons = bravo.icons();
            assertEquals(1, bravoIcons.size());
            assertEquals("file://bar", bravoIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", bravoIcons.getJsonObject(0).getString("mimeType"));

            PromptInfo charlie = page.findByName("charlie");
            JsonArray charlieIcons = charlie.icons();
            assertEquals(1, charlieIcons.size());
            assertEquals("file://baz", charlieIcons.getJsonObject(0).getString("src"));
            assertEquals("image/png", charlieIcons.getJsonObject(0).getString("mimeType"));
        }).thenAssertResults();
    }

    public static class MyPrompts {

        @Icons(AlphaIcons.class)
        @Prompt
        String alpha() {
            return "ok";
        }

        @Icons(BravoIcons.class)
        @Prompt
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
        PromptManager promptManager;

        @Override
        public List<Icon> get(FeatureInfo feature) {
            if (promptManager.getPrompt(feature.name()) != null) {
                return List.of(new Icon("file://bar", "image/png"));
            }
            return List.of();
        }

    }

}
