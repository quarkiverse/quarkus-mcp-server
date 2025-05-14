package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.runtime.util.ExceptionUtil;
import io.quarkus.test.QuarkusUnitTest;

public class WrongAnnotationUsageTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(StaticFeatures.class, InterfaceFeatures.class);
            })
            .assertException(t -> {
                Throwable rootCause = ExceptionUtil.getRootCause(t);
                String rootStr = rootCause.toString();
                assertTrue(rootStr.contains("PROMPT method must not be static"), rootStr);
                assertTrue(rootStr.contains("PROMPT method must not be declared on an interface"), rootStr);
                assertTrue(rootStr.contains("TOOL method must not be static"), rootStr);
                assertTrue(rootStr.contains("TOOL method must not be declared on an interface"), rootStr);
                assertTrue(rootStr.contains("RESOURCE method must not be static"), rootStr);
                assertTrue(rootStr.contains("RESOURCE method must not be declared on an interface"), rootStr);
                assertTrue(rootStr.contains("RESOURCE_TEMPLATE method must not be static"), rootStr);
                assertTrue(rootStr.contains("RESOURCE_TEMPLATE method must not be declared on an interface"), rootStr);
            });

    @Test
    public void test() {
        fail();
    }

    public static class StaticFeatures {

        @Prompt
        static PromptResponse foo() {
            return null;
        }

        @Tool
        static String bar() {
            return null;
        }

        @Resource(uri = "file://baz")
        static ResourceResponse baz() {
            return null;
        }

        @ResourceTemplate(uriTemplate = "file://{alpha}/baz")
        static TextResourceContents qux(String alpha) {
            return null;
        }

    }

    public interface InterfaceFeatures {

        @Prompt
        PromptResponse foo();

        @Tool
        String bar();

        @Resource(uri = "file://baz")
        ResourceResponse baz();

        @ResourceTemplate(uriTemplate = "file://{alpha}/baz")
        TextResourceContents qux(String alpha);

    }

}
