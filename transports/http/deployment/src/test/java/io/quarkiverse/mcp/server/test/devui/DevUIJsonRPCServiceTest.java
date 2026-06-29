package io.quarkiverse.mcp.server.test.devui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

public class DevUIJsonRPCServiceTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withApplicationRoot(root -> root.addClass(DevUIAppFeatures.class));

    public DevUIJsonRPCServiceTest() {
        super("quarkus-mcp-server-http");
    }

    @Test
    public void testGetToolsData() throws Exception {
        JsonNode tools = super.executeJsonRPCMethod("getToolsData");
        assertNotNull(tools);
        assertTrue(tools.isArray());
        assertEquals(2, tools.size());

        Set<String> toolNames = new HashSet<>();
        for (JsonNode tool : tools) {
            toolNames.add(tool.get("name").asText());
        }
        assertTrue(toolNames.contains("echo"));
        assertTrue(toolNames.contains("add"));

        JsonNode echoTool = findByName(tools, "echo");
        assertNotNull(echoTool);
        JsonNode echoArgs = echoTool.get("args");
        assertNotNull(echoArgs);
        assertEquals(1, echoArgs.size());
        assertEquals("message", echoArgs.get(0).get("name").asText());
        assertEquals("The message to echo", echoArgs.get(0).get("description").asText());
        assertEquals("string", echoArgs.get(0).get("type").asText());

        JsonNode addTool = findByName(tools, "add");
        assertNotNull(addTool);
        JsonNode addArgs = addTool.get("args");
        assertNotNull(addArgs);
        assertEquals(2, addArgs.size());
    }

    @Test
    public void testGetPromptsData() throws Exception {
        JsonNode prompts = super.executeJsonRPCMethod("getPromptsData");
        assertNotNull(prompts);
        assertTrue(prompts.isArray());
        assertEquals(1, prompts.size());

        JsonNode greet = prompts.get(0);
        assertEquals("greet", greet.get("name").asText());
        assertEquals("A greeting prompt", greet.get("description").asText());
        assertNotNull(greet.get("inputPrototype"));
    }

    @Test
    public void testGetPromptCompletionsData() throws Exception {
        JsonNode completions = super.executeJsonRPCMethod("getPromptCompletionsData");
        assertNotNull(completions);
        assertTrue(completions.isArray());
        assertEquals(1, completions.size());

        JsonNode completion = completions.get(0);
        assertEquals("greet", completion.get("name").asText());
        assertEquals("name", completion.get("argumentName").asText());
    }

    @Test
    public void testGetResourcesData() throws Exception {
        JsonNode resources = super.executeJsonRPCMethod("getResourcesData");
        assertNotNull(resources);
        assertTrue(resources.isArray());
        assertEquals(1, resources.size());

        JsonNode info = resources.get(0);
        assertEquals("file:///test/info", info.get("uri").asText());
    }

    @Test
    public void testGetResourceTemplatesData() throws Exception {
        JsonNode templates = super.executeJsonRPCMethod("getResourceTemplatesData");
        assertNotNull(templates);
        assertTrue(templates.isArray());
        assertEquals(1, templates.size());

        JsonNode template = templates.get(0);
        assertEquals("file:///test/{name}", template.get("uriTemplate").asText());
    }

    @Test
    public void testGetResourceTemplateCompletionsData() throws Exception {
        JsonNode completions = super.executeJsonRPCMethod("getResourceTemplateCompletionsData");
        assertNotNull(completions);
        assertTrue(completions.isArray());
        assertEquals(1, completions.size());

        JsonNode completion = completions.get(0);
        assertEquals("testTemplate", completion.get("name").asText());
        assertEquals("name", completion.get("argumentName").asText());
    }

    @Test
    public void testCallTool() throws Exception {
        JsonNode result = super.executeJsonRPCMethod("callTool",
                Map.of(
                        "name", "echo",
                        "serverName", "",
                        "args", "{\"message\":\"hello\"}",
                        "bearerToken", "",
                        "forceNewSession", true));
        assertNotNull(result);
        assertFalse(result.has("error"));
        JsonNode response = result.get("response");
        assertNotNull(response);
        JsonNode callResult = response.get("result");
        assertNotNull(callResult);
        JsonNode content = callResult.get("content");
        assertNotNull(content);
        assertTrue(content.isArray());
        assertEquals("Echo: hello", content.get(0).get("text").asText());
    }

    @Test
    public void testGetPrompt() throws Exception {
        JsonNode result = super.executeJsonRPCMethod("getPrompt",
                Map.of(
                        "name", "greet",
                        "serverName", "",
                        "args", "{\"name\":\"World\"}",
                        "bearerToken", "",
                        "forceNewSession", true));
        assertNotNull(result);
        assertFalse(result.has("error"));
        JsonNode response = result.get("response");
        assertNotNull(response);
        JsonNode promptResult = response.get("result");
        assertNotNull(promptResult);
        JsonNode messages = promptResult.get("messages");
        assertNotNull(messages);
        assertTrue(messages.isArray());
        assertEquals("Hello World", messages.get(0).get("content").get("text").asText());
    }

    private JsonNode findByName(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (name.equals(node.get("name").asText())) {
                return node;
            }
        }
        return null;
    }
}
