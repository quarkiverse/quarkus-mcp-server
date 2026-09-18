package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionSetting;
import io.quarkiverse.mcp.server.MetaField.Type;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ExtensionCapabilitiesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(SkillsExtension.class, TasksExtension.class, AcmeExtension.class));

    @Test
    public void testSse() {
        McpAssured.newSseClient().build().connect(ExtensionCapabilitiesTest::assertExtensions);
    }

    @Test
    public void testStreamable() {
        McpAssured.newStreamableClient().build().connect(ExtensionCapabilitiesTest::assertExtensions);
    }

    @Test
    public void testStreamableStateless() {
        // In the stateless protocol the capabilities are negotiated via server/discover
        McpAssured.newStreamableClient().setStateless().build().connect(ExtensionCapabilitiesTest::assertExtensions);
    }

    static void assertExtensions(InitResult initResult) {
        ServerCapability extensions = initResult.capabilities().stream()
                .filter(c -> c.name().equals("extensions"))
                .findFirst()
                .orElse(null);
        assertNotNull(extensions, "The extensions capability should be advertised");
        Map<String, Object> props = extensions.properties();

        // Skills - a single boolean setting
        assertEquals(Map.of("directoryRead", Boolean.TRUE), props.get("io.modelcontextprotocol/skills"));

        // Tasks - no settings => empty settings object
        assertEquals(Map.of(), props.get("io.modelcontextprotocol/tasks"));

        // Acme - one of each type, decoded to the proper JSON types
        @SuppressWarnings("unchecked")
        Map<String, Object> acme = (Map<String, Object>) props.get("com.acme/demo");
        assertNotNull(acme);
        assertEquals("hello", acme.get("strVal"));
        assertEquals(42, ((Number) acme.get("intVal")).intValue());
        assertEquals(Boolean.FALSE, acme.get("boolVal"));
        assertEquals(List.of("a", "b"), acme.get("jsonVal"));
    }

    @McpExtension(id = "io.modelcontextprotocol/skills")
    @McpExtensionSetting(name = "directoryRead", type = Type.BOOLEAN, value = "true")
    public static class SkillsExtension {
    }

    @McpExtension(id = "io.modelcontextprotocol/tasks")
    public static class TasksExtension {
    }

    @McpExtension(id = "com.acme/demo")
    @McpExtensionSetting(name = "strVal", value = "hello")
    @McpExtensionSetting(name = "intVal", type = Type.INT, value = "42")
    @McpExtensionSetting(name = "boolVal", type = Type.BOOLEAN, value = "false")
    @McpExtensionSetting(name = "jsonVal", type = Type.JSON, value = "[\"a\",\"b\"]")
    public static class AcmeExtension {
    }

}
