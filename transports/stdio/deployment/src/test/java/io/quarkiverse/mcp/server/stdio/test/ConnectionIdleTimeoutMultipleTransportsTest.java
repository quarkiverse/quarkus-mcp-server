package io.quarkiverse.mcp.server.stdio.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.deployment.TransportBuildItem;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * If stdio is combined with another transport then the default connection idle timeout is not changed.
 */
public class ConnectionIdleTimeoutMultipleTransportsTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            // Do not read stdin, otherwise the app would exit on EOF
            .overrideConfigKey("quarkus.mcp.server.stdio.initialization-enabled", "false")
            // Simulate another transport
            .addBuildChainCustomizer(buildChainBuilder -> buildChainBuilder.addBuildStep(context -> {
                context.produce(new TransportBuildItem(TransportBuildItem.HTTP));
            }).produces(TransportBuildItem.class).build());

    @Inject
    McpServersRuntimeConfig serversConfig;

    @Test
    public void testDefaultIdleTimeout() {
        assertEquals(Duration.ofMinutes(30), serversConfig.servers().get(McpServer.DEFAULT).connectionIdleTimeout());
    }

}
