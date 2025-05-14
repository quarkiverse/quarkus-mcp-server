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
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
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
        initClient();

        JsonObject toolListMessage = newMessage("tools/list");
        send(toolListMessage);
        JsonObject toolListResponse = waitForLastResponse();
        JsonObject toolListResult = assertResultResponse(toolListMessage, toolListResponse);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(1, tools.size());
        assertTool(tools, "bravo", null, schema -> {
            JsonObject properties = schema.getJsonObject("properties");
            assertEquals(1, properties.size());
            JsonObject priceProperty = properties.getJsonObject("price");
            assertNotNull(priceProperty);
            assertEquals("integer", priceProperty.getString("type"));
        });

        JsonArray batch = new JsonArray();
        JsonObject msg1 = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "alpha")
                        .put("arguments", new JsonObject()
                                .put("price", 10)));
        batch.add(msg1);
        JsonObject msg2 = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", "bravo")
                        .put("arguments", new JsonObject()
                                .put("price", 10)));
        batch.add(msg2);
        send(batch.encode());

        JsonObject r1 = client().waitForResponse(msg1);
        JsonObject error = assertErrorResponse(msg1, r1);
        assertEquals("Invalid tool name: alpha", error.getString("message"));

        JsonObject r2 = client().waitForResponse(msg2);
        JsonObject result = assertResultResponse(msg2, r2);
        assertNotNull(result);
        assertFalse(result.getBoolean("isError"));
        JsonArray content = result.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals("30", textContent.getString("text"));

    }

    @Test
    public void testPromptFilter() {
        initClient();

        JsonObject promptListMessage = newMessage("prompts/list");
        send(promptListMessage);
        JsonObject promptListResponse = client().waitForResponse(promptListMessage);
        JsonObject promptListResult = assertResultResponse(promptListMessage, promptListResponse);
        JsonArray prompts = promptListResult.getJsonArray("prompts");
        assertEquals(0, prompts.size());

        JsonObject msg = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", "charlie"));
        send(msg);
        JsonObject r = client().waitForResponse(msg);
        JsonObject error = assertErrorResponse(msg, r);
        assertEquals("Invalid prompt name: charlie", error.getString("message"));
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
        initClient();

        JsonObject resourceTemplateListMessage = newMessage("resources/templates/list");
        send(resourceTemplateListMessage);
        JsonObject resourceTemplateListResponse = client().waitForResponse(resourceTemplateListMessage);
        JsonObject resourceTemplateListResult = assertResultResponse(resourceTemplateListMessage, resourceTemplateListResponse);
        JsonArray resourceTemplates = resourceTemplateListResult.getJsonArray("resourceTemplates");
        assertEquals(1, resourceTemplates.size());

        JsonArray batch = new JsonArray();
        JsonObject msg1 = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", "file:///foxtrot/1"));
        batch.add(msg1);
        JsonObject msg2 = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", "file:///1"));
        batch.add(msg2);
        send(batch.encode());

        JsonObject r1 = client().waitForResponse(msg1);
        JsonObject error = assertErrorResponse(msg1, r1);
        assertEquals("Invalid resource uri: file:///foxtrot/1", error.getString("message"));

        JsonObject r2 = client().waitForResponse(msg2);
        JsonObject result = assertResultResponse(msg2, r2);
        assertNotNull(result);
        JsonArray contents = result.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject textContent = contents.getJsonObject(0);
        assertEquals("foo:1", textContent.getString("text"));
    }

    @Test
    public void testResourceFilter() {
        initClient();

        JsonObject resourceListMessage = newMessage("resources/list");
        send(resourceListMessage, Map.of("test-header", "foo"));
        JsonObject resourceListResponse = client().waitForResponse(resourceListMessage);
        JsonObject resourceListResult = assertResultResponse(resourceListMessage, resourceListResponse);
        JsonArray resources = resourceListResult.getJsonArray("resources");
        assertEquals(1, resources.size());

        JsonObject msg1 = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", "file:///project/delta"));
        send(msg1);
        JsonObject r1 = client().waitForResponse(msg1);
        JsonObject error = assertErrorResponse(msg1, r1);
        assertEquals("Invalid resource uri: file:///project/delta", error.getString("message"));

        JsonObject msg2 = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", "file:///project/delta"));
        send(msg2, Map.of("test-header", "foo"));
        JsonObject r2 = client().waitForResponse(msg2);
        JsonObject result = assertResultResponse(msg2, r2);
        assertNotNull(result);
        JsonArray contents = result.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject textContent = contents.getJsonObject(0);
        assertEquals("3", textContent.getString("text"));
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
