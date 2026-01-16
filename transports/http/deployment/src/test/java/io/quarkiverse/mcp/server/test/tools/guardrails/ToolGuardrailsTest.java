package io.quarkiverse.mcp.server.test.tools.guardrails;

import static io.quarkiverse.mcp.server.ExecutionModel.WORKER_THREAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.SupportedExecutionModels;
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
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

public class ToolGuardrailsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyInputGuardrail.class, MyOutputGuardrail1.class,
                            MyOutputGuardrail2.class, EmailFormatValidator.class));

    @Inject
    ToolManager toolManager;

    @Test
    public void testGuardrails() {
        toolManager.newTool("charlie")
                .setDescription("Charlie tool")
                .addArgument("age", "Age", true, int.class)
                .setInputGuardrails(List.of(CharlieInputGuardrail.class))
                .setOutputGuardrails(List.of(MyOutputGuardrail1.class, MyOutputGuardrail2.class))
                .setHandler(toolArguments -> {
                    return ToolResponse.success(toolArguments.args().get("age").toString());
                })
                .register();

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("alpha", Map.of("bravo", "ping"), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("ko:ok:PING", toolResponse.firstContent().asText().text());
                })
                .toolsCall("mail", Map.of("to", "wrong_@", "body", "hey"), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals("Invalid email format: wrong_@", toolResponse.content().get(0).asText().text());
                })
                .toolsCall("charlie", Map.of("age", 10), toolResponse -> {
                    assertFalse(toolResponse.isError());
                    assertEquals("ko:ok:20", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();

        MyInputGuardrail.fail = true;

        client.when()
                .toolsCall("alpha", Map.of("bravo", "ping"), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals("Fail!", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();

        MyInputGuardrail.fail = false;
        MyOutputGuardrail1.fail = true;

        client.when()
                .toolsCall("alpha", Map.of("bravo", "ping"), toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals("FAIL!", toolResponse.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @ToolGuardrails(input = MyInputGuardrail.class, output = { MyOutputGuardrail1.class, MyOutputGuardrail2.class })
        @Tool
        String alpha(String bravo) {
            return bravo;
        }

        @ToolGuardrails(input = EmailFormatValidator.class)
        @Tool
        String mail(String to, String body) {
            return "Mail sent to: " + to;
        }

    }

    @Singleton
    public static class MyInputGuardrail implements ToolInputGuardrail {

        static volatile boolean fail = false;

        @Override
        public void apply(ToolInputContext context) {
            if (!context.getTool().name().equals("alpha")) {
                throw new IllegalStateException();
            }
            if (fail) {
                throw new ToolCallException("Fail!");
            }
            context.setArguments(new JsonObject().put("bravo", context.getArguments().getString("bravo").toUpperCase()));
        }

    }

    @Singleton
    public static class MyOutputGuardrail1 implements ToolOutputGuardrail {

        static volatile boolean fail = false;

        @Override
        public void apply(ToolOutputContext context) {
            if (!context.getResponse().isError()) {
                if (fail) {
                    context.setResponse(ToolResponse.error("FAIL!"));
                } else {
                    context.setResponse(ToolResponse.success("ok:" + context.getResponse().firstContent().asText().text()));
                }
            }
        }

    }

    @SupportedExecutionModels(WORKER_THREAD)
    @Singleton
    public static class MyOutputGuardrail2 implements ToolOutputGuardrail {

        @Override
        public Uni<Void> applyAsync(ToolOutputContext context) {
            if (!context.getResponse().isError()) {
                context.setResponse(ToolResponse.success("ko:" + context.getResponse().firstContent().asText().text()));
            }
            return Uni.createFrom().voidItem();
        }

    }

    public static class EmailFormatValidator implements ToolInputGuardrail {

        private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

        @Override
        public void apply(ToolInputContext context) {
            String email = context.getArguments().getString("to");
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new ToolCallException("Invalid email format: " + email);
            }
        }
    }

    public static class CharlieInputGuardrail implements ToolInputGuardrail {

        @Override
        public void apply(ToolInputContext context) {
            if (!context.getTool().name().equals("charlie")) {
                throw new IllegalStateException();
            }
            context.setArguments(new JsonObject().put("age", context.getArguments().getInteger("age") * 2));
        }

    }

}
