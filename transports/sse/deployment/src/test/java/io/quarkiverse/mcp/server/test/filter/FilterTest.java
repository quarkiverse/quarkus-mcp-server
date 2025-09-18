package io.quarkiverse.mcp.server.test.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptFilter;
import io.quarkiverse.mcp.server.PromptManager.PromptInfo;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceFilter;
import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateFilter;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.ResourceTemplateManager.ResourceTemplateInfo;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolFilter;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

public class FilterTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class, MyFilter.class, AnotherFilter.class));

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Test
    public void testToolFilter() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsList(page -> {
                    assertEquals(1, page.tools().size());
                    io.quarkiverse.mcp.server.test.McpAssured.ToolInfo tool = page.tools().get(0);
                    assertEquals("bravo", tool.name());
                    JsonObject schema = tool.inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertEquals(1, properties.size());
                    JsonObject priceProperty = properties.getJsonObject("price");
                    assertNotNull(priceProperty);
                    assertEquals("integer", priceProperty.getString("type"));
                })
                .toolsCall("alpha")
                .withArguments(Map.of("price", 10))
                .withErrorAssert(error -> {
                    assertEquals("Invalid tool name: alpha", error.message());
                })
                .send()
                .toolsCall("bravo", Map.of("price", 10), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("30", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    @Test
    public void testPromptFilter() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .promptsList(page -> {
                    assertEquals(0, page.size());
                })
                .promptsGet("charlie")
                .withErrorAssert(error -> assertEquals("Invalid prompt name: charlie", error.message()))
                .send()
                .thenAssertResults();
    }

    @Test
    public void testResourceTemplateFilter() {
        resourceTemplateManager.newResourceTemplate("foxtrot")
                .setUriTemplate("file:///foxtrot/{foo}")
                .setDescription("Foxtrot description!")
                .setHandler(
                        args -> new ResourceResponse(
                                List.of(TextResourceContents.create(args.requestUri().value(), args.args().get("foo")))))
                .register();

        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .resourcesTemplatesList(page -> {
                    assertEquals(1, page.size());
                })
                .resourcesRead("file:///foxtrot/1")
                .withErrorAssert(error -> assertEquals("Resource not found: file:///foxtrot/1", error.message()))
                .send()
                .resourcesRead("file:///1", response -> {
                    assertEquals("foo:1", response.contents().get(0).asText().text());
                })
                .thenAssertResults();
    }

    @Test
    public void testResourceFilter() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .addHeader("test-header", "foo")
                .resourcesList(page -> {
                    assertEquals(1, page.size());
                })
                .resourcesRead("file:///project/delta", response -> {
                    assertEquals("3", response.contents().get(0).asText().text());
                })
                .thenAssertResults();

        client.when()
                .resourcesRead("file:///project/delta")
                .withErrorAssert(error -> assertEquals("Resource not found: file:///project/delta", error.message()))
                .send()
                .thenAssertResults();
    }

    public static class MyFeatures {

        @Tool
        String alpha(int price) {
            return "" + price * 2;
        }

        @Tool
        String bravo(int price) {
            return "" + price * 3;
        }

        @Prompt
        PromptResponse charlie() {
            return PromptResponse.withMessages(PromptMessage.withUserRole("charlie"));
        }

        @Resource(uri = "file:///project/delta")
        TextResourceContents delta(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "3");
        }

        @ResourceTemplate(uriTemplate = "file:///{path}")
        TextResourceContents echo(String path, RequestUri uri) {
            return TextResourceContents.create(uri.value(), "foo:" + path);
        }

    }

    // @Singleton added automatically
    public static class MyFilter implements ToolFilter, PromptFilter, ResourceFilter, ResourceTemplateFilter {

        @Inject
        HttpServerRequest request;

        @Override
        public boolean test(PromptInfo prompt, McpConnection connection) {
            return connection.initialRequest().supportsSampling();
        }

        @Override
        public boolean test(ToolInfo tool, McpConnection connection) {
            return tool.name().equals("bravo");
        }

        @Override
        public boolean test(ResourceTemplateInfo resourceTemplate, McpConnection connection) {
            // Skip templates registered programmatically
            return resourceTemplate.isMethod();
        }

        @Override
        public boolean test(ResourceInfo resource, McpConnection connection) {
            return request.getHeader("test-header") != null;
        }

    }

    @Singleton
    @Priority(100)
    public static class AnotherFilter implements PromptFilter {

        @Override
        public boolean test(PromptInfo promptInfo, McpConnection connection) {
            // doesn't matter since MyFilter#test() returns false
            return true;
        }

    }

}
