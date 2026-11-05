package io.quarkiverse.mcp.server.test.tools.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.GlobalGuardrail;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolInputGuardrail;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolOutputGuardrail;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.Startup;
import io.quarkus.test.QuarkusUnitTest;

public class GlobalGuardrailsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, GlobalInputHigh.class, GlobalInputLow.class, ToolInput.class,
                            GlobalOutputHigh.class, GlobalOutputLow.class, ToolOutput.class,
                            GlobalGuardrailProducer.class));

    @Test
    public void testGlobalGuardrails() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                // global input (by priority) -> declared input -> tool -> declared output -> global output (by priority)
                // ":gp" is added by a produced global guardrail with priority 5
                .toolsCall("alpha", Map.of("val", "x"), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("x:gi1:gi2:ti:to:go1:gp:go2", toolResponse.firstContent().asText().text());
                })
                // no declared guardrails
                .toolsCall("bravo", Map.of("val", "x"), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("x:gi1:gi2:go1:gp:go2", toolResponse.firstContent().asText().text());
                })
                // global output guardrail also declared on the tool is only executed once
                .toolsCall("charlie", Map.of("val", "x"), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("x:gi1:gi2:to:go1:gp:go2", toolResponse.firstContent().asText().text());
                })
                // ToolCallException is converted to an error response before output guardrails are executed
                .toolsCall("delta", Map.of("val", "x"), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals("x:gi1:gi2:go1:gp:go2", toolResponse.firstContent().asText().text());
                })
                // programmatically registered tool
                .toolsCall("echo", Map.of("val", "x"), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("x:gi1:gi2:go1:gp:go2", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();

        // @GlobalGuardrail is a qualifier - the bean does not have @Default
        assertFalse(Arc.container().select(GlobalInputLow.class).isResolvable());
        // The default scope of a global guardrail is @Singleton
        assertEquals(Singleton.class,
                Arc.container().instance(GlobalInputLow.class, GlobalGuardrail.Literal.INSTANCE).getBean().getScope());
        // A single instance is shared by all tools
        assertEquals(1, GlobalInputLow.CREATED.get());
    }

    public static class MyTools {

        @Inject
        ToolManager toolManager;

        @ToolGuardrails(input = ToolInput.class, output = ToolOutput.class)
        @Tool
        String alpha(String val) {
            return val;
        }

        @Tool
        String bravo(String val) {
            return val;
        }

        @ToolGuardrails(output = { GlobalOutputLow.class, ToolOutput.class })
        @Tool
        String charlie(String val) {
            return val;
        }

        @Tool
        String delta(String val) {
            throw new ToolCallException(val);
        }

        @Startup
        void registerTool() {
            toolManager.newTool("echo")
                    .setDescription("Echo the value")
                    .addArgument("val", "The value", true, String.class)
                    .setHandler(args -> ToolResponse.success(args.args().get("val").toString()))
                    .register();
        }

    }

    static void appendArg(ToolInputGuardrail.ToolInputContext context, String suffix) {
        context.setArguments(context.getArguments().copy().put("val", context.getArguments().getString("val") + suffix));
    }

    static void appendText(ToolOutputGuardrail.ToolOutputContext context, String suffix) {
        ToolResponse response = context.getResponse();
        context.setResponse(new ToolResponse(response.isError(),
                List.of(new TextContent(response.firstContent().asText().text() + suffix))));
    }

    @Priority(10)
    @GlobalGuardrail
    public static class GlobalInputHigh implements ToolInputGuardrail {

        @Override
        public void apply(ToolInputContext context) {
            appendArg(context, ":gi1");
        }

    }

    // No scope declared - @Singleton is added automatically
    @GlobalGuardrail
    public static class GlobalInputLow implements ToolInputGuardrail {

        static final AtomicInteger CREATED = new AtomicInteger();

        public GlobalInputLow() {
            CREATED.incrementAndGet();
        }

        @Override
        public void apply(ToolInputContext context) {
            appendArg(context, ":gi2");
        }

    }

    @Singleton
    public static class ToolInput implements ToolInputGuardrail {

        @Override
        public void apply(ToolInputContext context) {
            appendArg(context, ":ti");
        }

    }

    @Priority(10)
    @GlobalGuardrail
    @Singleton
    public static class GlobalOutputHigh implements ToolOutputGuardrail {

        @Override
        public void apply(ToolOutputContext context) {
            appendText(context, ":go1");
        }

    }

    @GlobalGuardrail
    @Singleton
    public static class GlobalOutputLow implements ToolOutputGuardrail {

        @Override
        public void apply(ToolOutputContext context) {
            appendText(context, ":go2");
        }

    }

    @Singleton
    public static class ToolOutput implements ToolOutputGuardrail {

        @Override
        public void apply(ToolOutputContext context) {
            appendText(context, ":to");
        }

    }

    @Singleton
    public static class GlobalGuardrailProducer {

        @Priority(5)
        @GlobalGuardrail
        @Produces
        @Singleton
        ToolOutputGuardrail producedGuardrail() {
            return new ToolOutputGuardrail() {

                @Override
                public void apply(ToolOutputContext context) {
                    appendText(context, ":gp");
                }
            };
        }

    }

}
