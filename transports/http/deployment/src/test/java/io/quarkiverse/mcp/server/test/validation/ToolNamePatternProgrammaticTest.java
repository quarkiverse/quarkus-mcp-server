package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ToolNamePatternProgrammaticTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.tools.name-pattern",
                    Tool.SPEC_NAME_PATTERN);

    @Inject
    MyTools myTools;

    @Test
    public void testValidName() {
        myTools.register("getUser");
        myTools.register("DATA_EXPORT_v2");
        myTools.register("admin.tools.list");
        myTools.register("alpha-tool1");
    }

    @Test
    public void testInvalidName() {
        assertThrows(IllegalStateException.class, () -> myTools.register("tool with spaces"));
        assertThrows(IllegalStateException.class, () -> myTools.register("tool,name"));
        assertThrows(IllegalStateException.class, () -> myTools.register("tool@name"));
    }

    @Singleton
    public static class MyTools {

        @Inject
        ToolManager manager;

        void register(String name) {
            manager.newTool(name)
                    .setDescription(name + " description")
                    .setHandler(args -> ToolResponse.success(name))
                    .register();
        }

    }

}
