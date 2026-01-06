package io.quarkiverse.mcp.server.test.initrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.Icon.Theme;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.InitialRequest.Transport;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class InitialRequestTest extends McpServerTest {

    private static final String TITLE = "Foo";
    private static final String VERSION = "1.0";
    private static final String DESCRIPTION = "This is an MCP client!";
    private static final String URL = "https://github.com/quarkiverse/quarkus-mcp-server";

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClass(MyTools.class));

    @Test
    public void testInitRequest() {
        McpSseTestClient client = McpAssured.newSseClient()
                .setClientCapabilities(new ClientCapability(ClientCapability.SAMPLING, Map.of()))
                .setTitle(TITLE)
                .setVersion(VERSION)
                .setDescription(DESCRIPTION)
                .setWebsiteUrl(URL)
                .setIcons(new Icon(URL, "image/png", List.of("1x1"), Theme.LIGHT))
                .build()
                .connect();

        client.when()
                .toolsCall("testInitRequest", r -> {
                    assertFalse(r.isError());
                    assertEquals("ok", r.content().get(0).asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String testInitRequest(McpConnection connection) {
            InitialRequest initRequest = connection.initialRequest();
            if (initRequest != null) {
                if (!initRequest.protocolVersion().equals(McpMessageHandler.SUPPORTED_PROTOCOL_VERSIONS.get(0))) {
                    throw new IllegalStateException();
                }
                if (!initRequest.implementation().name().equals("test-client")) {
                    throw new IllegalStateException();
                }
                if (initRequest.clientCapabilities().size() != 1
                        || !initRequest.clientCapabilities().get(0).name().equals("sampling")) {
                    throw new IllegalStateException();
                }
                if (initRequest.transport() != Transport.SSE) {
                    throw new IllegalStateException();
                }
                if (!initRequest.implementation().title().equals(TITLE)) {
                    throw new IllegalStateException();
                }
                if (!initRequest.implementation().description().equals(DESCRIPTION)) {
                    throw new IllegalStateException();
                }
                if (!initRequest.implementation().version().equals(VERSION)) {
                    throw new IllegalStateException();
                }
                if (!initRequest.implementation().websiteUrl().equals(URL)) {
                    throw new IllegalStateException();
                }
                Icon icon = initRequest.implementation().icons().get(0);
                if (!icon.src().equals(URL) || !icon.mimeType().equals("image/png") || icon.theme() != Theme.LIGHT
                        || !icon.sizes().get(0).equals("1x1")) {
                    throw new IllegalStateException();
                }
            }
            return "ok";
        }
    }

}
