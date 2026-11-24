package io.quarkiverse.mcp.server.http.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import io.quarkus.vertx.http.HttpServerStart;
import io.quarkus.vertx.http.HttpsServerStart;
import io.vertx.core.http.HttpServerConfig;
import io.vertx.core.net.ServerSSLOptions;

public class McpServerEndpointsLoggerTest {

    @Test
    public void testHttpBaseUri() {
        HttpServerConfig config = new HttpServerConfig().setHost("localhost").setPort(8080);
        assertEquals("http://localhost:8080", McpServerEndpointsLogger.baseUri(new HttpServerStart(config)));
    }

    @Test
    public void testHttpsBaseUri() {
        // Quarkus does not set the ssl flag on the TCP config; in Vert.x 5 the TLS is derived from the ServerSSLOptions
        HttpServerConfig config = new HttpServerConfig().setHost("localhost").setPort(8443);
        assertFalse(config.getTcpConfig().isSsl());
        assertEquals("https://localhost:8443",
                McpServerEndpointsLogger.baseUri(new HttpsServerStart(config, new ServerSSLOptions())));
    }

}
