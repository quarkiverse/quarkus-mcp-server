package io.quarkiverse.mcp.server.test;

import java.net.URI;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;

public class McpQuarkusTestBeforeEachCallback implements QuarkusTestBeforeEachCallback {

    private static final Logger LOG = Logger.getLogger(McpQuarkusTestBeforeEachCallback.class);

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        try {
            McpAssured.baseUri = new URI(ConfigProvider.getConfig().getValue("test.url", String.class));
        } catch (Throwable e) {
            LOG.warnf("Cannot set the baseUri for McpAssured: %s", e);
        }
    }

}
