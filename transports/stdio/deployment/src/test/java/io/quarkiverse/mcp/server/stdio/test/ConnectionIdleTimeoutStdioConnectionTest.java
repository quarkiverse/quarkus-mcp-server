package io.quarkiverse.mcp.server.stdio.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.stream.StreamSupport;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.deployment.TransportBuildItem;
import io.quarkiverse.mcp.server.runtime.ConnectionManager;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * The stdio connection is never removed due to inactivity, even if the connection idle timeout is set.
 */
public class ConnectionIdleTimeoutStdioConnectionTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            // The stdio transport is initialized manually in the test
            .overrideConfigKey("quarkus.mcp.server.stdio.initialization-enabled", "false")
            .overrideConfigKey("quarkus.mcp.server.connection-idle-timeout", "200ms")
            // Simulate another transport
            .addBuildChainCustomizer(buildChainBuilder -> buildChainBuilder.addBuildStep(context -> {
                context.produce(new TransportBuildItem(TransportBuildItem.HTTP));
            }).produces(TransportBuildItem.class).build());

    @Inject
    McpServersRuntimeConfig serversConfig;

    @Inject
    StdioMcpMessageHandler messageHandler;

    @Inject
    ConnectionManager connectionManager;

    @Test
    public void testStdioConnectionNotRemoved() throws IOException, InterruptedException {
        assertEquals(Duration.ofMillis(200), serversConfig.servers().get(McpServer.DEFAULT).connectionIdleTimeout());

        // Keep the stdin open so that the app does not exit on EOF
        PipedOutputStream stdin = new PipedOutputStream();
        InputStream originalIn = System.in;
        System.setIn(new PipedInputStream(stdin));
        try {
            messageHandler.initialize(new PrintStream(new ByteArrayOutputStream()));
        } finally {
            System.setIn(originalIn);
        }
        assertEquals(1, connectionCount());

        // The idle check is fired every 100 ms
        Thread.sleep(1000);
        assertEquals(1, connectionCount());
    }

    private long connectionCount() {
        return StreamSupport.stream(connectionManager.spliterator(), false).count();
    }

}
