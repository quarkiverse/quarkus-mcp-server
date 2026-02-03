package io.quarkiverse.mcp.server.test.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.FilterContext;
import io.quarkiverse.mcp.server.McpMethod;
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
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

public class FilterContextTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig(2000)
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class, MyFilter.class));

    @Inject
    ResourceTemplateManager resourceTemplateManager;

    @Test
    public void testToolFilter() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList()
                .withMetadata(Map.of("foo", true))
                .withAssert(page -> {
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
                .send()
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
                .promptsList()
                .withMetadata(Map.of("foo", true))
                .withAssert(page -> {
                    assertEquals(0, page.size());
                })
                .send()
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
                .resourcesTemplatesList()
                .withMetadata(Map.of("foo", true))
                .withAssert(page -> {
                    assertEquals(1, page.size());
                })
                .send()
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
                .resourcesList()
                .withMetadata(Map.of("foo", true))
                .withAssert(page -> {
                    assertEquals(1, page.size());
                })
                .send()
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
        public boolean test(PromptInfo prompt, FilterContext context) {
            return context.connection().initialRequest().supportsSampling()
                    && context.meta().asJsonObject().getBoolean("foo")
                    && context.requestId() != null
                    && (context.method() == McpMethod.PROMPTS_GET || context.method() == McpMethod.PROMPTS_LIST);
        }

        @Override
        public boolean test(ToolInfo tool, FilterContext context) {
            return tool.name().equals("bravo")
                    && context.meta().asJsonObject().getBoolean("foo")
                    && (context.method() == McpMethod.TOOLS_CALL || context.method() == McpMethod.TOOLS_LIST);
        }

        @Override
        public boolean test(ResourceTemplateInfo resourceTemplate, FilterContext context) {
            // Skip templates registered programmatically
            return resourceTemplate.isMethod()
                    && context.meta().asJsonObject().getBoolean("foo")
                    && (context.method() == McpMethod.RESOURCE_TEMPLATES_LIST
                            || context.method() == McpMethod.RESOURCES_READ);
        }

        @Override
        public boolean test(ResourceInfo resource, FilterContext context) {
            return request.getHeader("test-header") != null
                    && context.meta().asJsonObject().getBoolean("foo")
                    && (context.method() == McpMethod.RESOURCES_LIST
                            || context.method() == McpMethod.RESOURCES_READ);
        }

    }

}
