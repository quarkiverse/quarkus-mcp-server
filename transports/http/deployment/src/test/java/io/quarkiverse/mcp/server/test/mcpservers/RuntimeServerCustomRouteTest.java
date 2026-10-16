package io.quarkiverse.mcp.server.test.mcpservers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Serves MCP servers created at runtime (one per tenant, unknown at build time) from an application route that delegates
 * to {@link StreamableHttpMcpMessageHandler#handle(io.vertx.ext.web.RoutingContext, String)}, and verifies that all three
 * methods work through it: POST, the GET subsidiary SSE stream (so stateful clients receive
 * {@code notifications/tools/list_changed}, scoped to the affected server) and DELETE.
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-mcp-server/issues/1024">#1024</a>
 */
public class RuntimeServerCustomRouteTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(RuntimeServerCustomRouteTest.class, TenantRoutes.class))
            // Tenants are not configured at build time
            .overrideConfigKey("quarkus.mcp.server.invalid-server-name-strategy", "ignore")
            // So that the subsidiary SSE debug notification is sent when the GET stream opens
            .overrideRuntimeConfigKey("quarkus.mcp.server.tenant-a.client-logging.default-level", "DEBUG")
            .overrideRuntimeConfigKey("quarkus.mcp.server.tenant-b.client-logging.default-level", "DEBUG");

    @Inject
    ToolManager toolManager;

    @Test
    public void testRuntimeServersServedFromCustomRoute() {
        // connect() opens the GET stream and waits for its debug notification: the stream is reachable through the route
        McpStreamableTestClient tenantA = McpAssured.newStreamableClient()
                .setMcpPath("/tenants/tenant-a/mcp")
                .setOpenSubsidiarySse(true)
                .build()
                .connect();
        McpStreamableTestClient tenantB = McpAssured.newStreamableClient()
                .setMcpPath("/tenants/tenant-b/mcp")
                .setOpenSubsidiarySse(true)
                .build()
                .connect();
        tenantA.waitForNotifications(1);
        tenantB.waitForNotifications(1);

        toolManager.newTool("alpha")
                .setServerName("tenant-a")
                .setDescription("Alpha tool")
                .setHandler(ta -> ToolResponse.success("alpha"))
                .register();

        // Only the stateful client of tenant-a is notified, on its GET stream
        List<JsonObject> notifications = tenantA.waitForNotifications(2).notifications();
        assertEquals("notifications/tools/list_changed", notifications.get(1).getString("method"));
        assertEquals(1, tenantB.snapshot().notifications().size());

        tenantA.when()
                .toolsList(page -> {
                    assertEquals(1, page.tools().size());
                    assertEquals("alpha", page.tools().get(0).name());
                })
                .toolsCall("alpha", r -> assertEquals("alpha", r.firstContent().asText().text()))
                .thenAssertResults();
        tenantB.when()
                .toolsList(page -> assertEquals(0, page.tools().size()))
                .thenAssertResults();

        // Any other method is rejected
        RestAssured.given()
                .when()
                .put(testUri.resolve("/tenants/tenant-a/mcp"))
                .then()
                .statusCode(405)
                .header("Allow", "GET, POST, DELETE");

        assertNotNull(toolManager.removeTool("alpha", "tenant-a"));
        // DELETE terminates the sessions
        tenantA.disconnect();
        tenantB.disconnect();
    }

    @Singleton
    public static class TenantRoutes {

        @Inject
        StreamableHttpMcpMessageHandler handler;

        void registerRoutes(@Observes Router router) {
            router.route("/tenants/:tenant/mcp")
                    .handler(BodyHandler.create())
                    .handler(ctx -> handler.handle(ctx, ctx.pathParam("tenant")));
        }
    }

}
