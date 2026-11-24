package io.quarkiverse.mcp.server.test;

import java.net.URI;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;

public class McpQuarkusTestBeforeEachCallback implements QuarkusTestBeforeEachCallback {

    private static final Logger LOG = Logger.getLogger(McpQuarkusTestBeforeEachCallback.class);

    // Registered by the HTTP server for @QuarkusTest (compatibility key)
    static final String TEST_URL = "test.url";
    // Registered by the test framework for @QuarkusIntegrationTest, where the app runs in a separate process
    static final String LOCAL_BASE_URI = "quarkus.http.local-base-uri";

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        try {
            Config config = ConfigProvider.getConfig();
            Optional<String> url = config.getOptionalValue(TEST_URL, String.class)
                    .or(() -> config.getOptionalValue(LOCAL_BASE_URI, String.class));
            if (url.isPresent()) {
                McpAssured.baseUri = new URI(url.get());
            } else {
                LOG.warnf("Cannot set the baseUri for McpAssured: neither %s nor %s is available", TEST_URL,
                        LOCAL_BASE_URI);
            }
        } catch (Throwable e) {
            LOG.warnf("Cannot set the baseUri for McpAssured: %s", e);
        }
    }

}
