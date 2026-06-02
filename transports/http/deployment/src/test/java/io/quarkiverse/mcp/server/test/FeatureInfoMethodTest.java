package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalInt;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.QuarkusUnitTest;

public class FeatureInfoMethodTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class));

    @Inject
    ToolManager toolManager;

    @Test
    public void testMethodBackedTool() {
        ToolManager.ToolInfo alphaInfo = toolManager.getTool("alpha");
        assertTrue(alphaInfo.isMethod());
        Optional<Method> method = alphaInfo.method();
        assertTrue(method.isPresent());
        assertEquals("alpha", method.get().getName());
        assertEquals(MyTools.class, method.get().getDeclaringClass());
        assertArrayEquals(
                new Class<?>[] { String.class, int.class, Optional.class, OptionalInt.class, McpConnection.class },
                method.get().getParameterTypes());
        Deprecated deprecated = method.get().getAnnotation(Deprecated.class);
        assertNotNull(deprecated);
        assertEquals("use bravo instead", deprecated.since());
    }

    @Test
    public void testProgrammaticTool() {
        toolManager.newTool("programmatic")
                .setDescription("A programmatic tool")
                .setHandler(args -> ToolResponse.success("ok"))
                .register();

        ToolManager.ToolInfo progInfo = toolManager.getTool("programmatic");
        assertFalse(progInfo.isMethod());
        assertTrue(progInfo.method().isEmpty());

        toolManager.removeTool("programmatic");
    }

    public static class MyTools {

        @Deprecated(since = "use bravo instead")
        @Tool
        String alpha(String value, int count, Optional<String> optional, OptionalInt optionalInt,
                McpConnection connection) {
            return value.toUpperCase();
        }
    }
}
