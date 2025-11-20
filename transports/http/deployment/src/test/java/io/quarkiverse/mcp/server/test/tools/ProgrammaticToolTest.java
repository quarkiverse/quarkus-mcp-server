package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolArguments;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ProgrammaticToolTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class))
            .overrideConfigKey("quarkus.mcp.server.tools.name-max-length", "10");

    @Inject
    MyTools myTools;

    @Test
    public void testTools() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsList(page -> assertEquals(0, page.size()))
                .toolsCall("alpha")
                .withErrorAssert(e -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, e.code());
                    assertEquals("Invalid tool name: alpha", e.message());
                }).send()
                .thenAssertResults();

        myTools.register("alpha", "2");
        assertThrows(IllegalArgumentException.class, () -> myTools.register("alpha", "2"));
        assertThrows(NullPointerException.class, () -> myTools.register(null, "2"));
        assertThrows(IllegalStateException.class, () -> myTools.tryTooLongName());

        List<JsonObject> notifications = client.waitForNotifications(1).notifications();
        assertEquals("notifications/tools/list_changed", notifications.get(0).getString("method"));

        client.when()
                .toolsList(page -> {
                    assertEquals(1, page.size());
                    assertEquals("ALPHA", page.tools().get(0).title());
                    assertEquals("ALPHA", page.tools().get(0).meta().getString("upperCaseName"));
                })
                .toolsCall("alpha", Map.of("foo", 2), r -> assertEquals("22", r.content().get(0).asText().text()))
                .thenAssertResults();

        ToolArguments lastArgs = MyTools.lastArgs.get();
        assertNotNull(lastArgs.connection());
        assertNotNull(lastArgs.progress());
        assertNotNull(lastArgs.requestId());
        assertNotNull(lastArgs.log());
        assertNotNull(lastArgs.roots());
        assertNotNull(lastArgs.sampling());
        assertNotNull(lastArgs.cancellation());

        myTools.register("bravo", "3");

        client.when()
                .toolsList(page -> assertEquals(2, page.size()))
                .toolsCall("bravo", Map.of("foo", 3), r -> assertEquals("33", r.content().get(0).asText().text()))
                .thenAssertResults();

        assertEquals("notifications/tools/list_changed",
                client.waitForNotifications(2).notifications().get(1).getString("method"));

        myTools.remove("alpha");

        client.when()
                .toolsList(page -> assertEquals(1, page.size()))
                .toolsCall("bravo", Map.of("foo", 4), r -> assertEquals("34", r.content().get(0).asText().text()))
                .thenAssertResults();
    }

    @Singleton
    public static class MyTools {

        static final AtomicReference<ToolArguments> lastArgs = new AtomicReference<>();

        @Inject
        ToolManager manager;

        void tryTooLongName() {
            manager.newTool("foo".repeat(4))
                    .setDescription("foo")
                    .setHandler(args -> null)
                    .register();
        }

        void register(String name, String result) {
            manager.newTool(name)
                    .setTitle(name.toUpperCase())
                    .setDescription(name + " description!")
                    .addArgument("foo", "Foo arg", true, int.class)
                    .setHandler(
                            toolArgs -> {
                                lastArgs.set(toolArgs);
                                return ToolResponse.success(result + toolArgs.args().get("foo"));
                            })
                    .setMetadata(Map.of(MetaKey.of("upperCaseName"), name.toUpperCase()))
                    .register();
        }

        ToolManager.ToolInfo remove(String name) {
            return manager.removeTool(name);
        }

    }

}
