package io.quarkiverse.mcp.server.test.stateless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class StatelessErrorCodesTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(MyTools.class, MyResources.class));

    @Test
    public void testMissingClientCapabilitySampling() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsCall("useSampling")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY, error.code());
                    assertTrue(error.message().contains("sampling"));
                    assertNotNull(error.data());
                    assertNotNull(error.data().getJsonObject("requiredCapabilities").getJsonObject("sampling"));
                })
                .send()
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testMissingClientCapabilityElicitation() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsCall("useElicitation")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY, error.code());
                    assertTrue(error.message().contains("elicitation"));
                    assertNotNull(error.data());
                    assertNotNull(error.data().getJsonObject("requiredCapabilities").getJsonObject("elicitation"));
                })
                .send()
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testMissingClientCapabilityRoots() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .toolsCall("useRoots")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.MISSING_REQUIRED_CLIENT_CAPABILITY, error.code());
                    assertTrue(error.message().contains("roots"));
                    assertNotNull(error.data());
                    assertNotNull(error.data().getJsonObject("requiredCapabilities").getJsonObject("roots"));
                })
                .send()
                .thenAssertResults();
        client.disconnect();
    }

    @Test
    public void testResourceNotFoundUsesInvalidParams() {
        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setStateless()
                .build()
                .connect();

        client.when()
                .resourcesRead("file:///nonexistent")
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code());
                    assertEquals("Resource not found: file:///nonexistent", error.message());
                    assertNull(error.data());
                })
                .send()
                .thenAssertResults();
        client.disconnect();
    }

    public static class MyTools {

        @Tool
        String useSampling(Sampling sampling) {
            sampling.requestBuilder();
            return "unreachable";
        }

        @Tool
        String useElicitation(Elicitation elicitation) {
            elicitation.requestBuilder();
            return "unreachable";
        }

        @Tool
        String useRoots(Roots roots) {
            roots.list();
            return "unreachable";
        }
    }

    public static class MyResources {

        @Resource(uri = "file:///project/alpha")
        ResourceResponse alpha(RequestUri uri) {
            return new ResourceResponse(List.of(new TextResourceContents(uri.value(), "content", null)));
        }
    }
}
