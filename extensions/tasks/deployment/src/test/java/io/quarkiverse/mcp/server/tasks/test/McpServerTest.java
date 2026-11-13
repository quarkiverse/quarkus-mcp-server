package io.quarkiverse.mcp.server.tasks.test;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.test.common.http.TestHTTPResource;

public abstract class McpServerTest {

    @TestHTTPResource
    protected URI testUri;

    public static QuarkusExtensionTest defaultConfig() {
        return defaultConfig(500);
    }

    public static QuarkusExtensionTest defaultConfig(int textLimit) {
        QuarkusExtensionTest config = new QuarkusExtensionTest();
        // Disable OTel and Micrometer by default
        config.overrideConfigKey("quarkus.otel.enabled", "false");
        config.overrideConfigKey("quarkus.micrometer.enabled", "false");
        // Enabled traffic logging if -DlogTraffic is used
        if (System.getProperty("logTraffic") != null) {
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.enabled", "true");
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.text-limit", "" + textLimit);
        }
        // Set the default log level to DEBUG so that the subsidiary SSE debug notification is sent
        config.overrideRuntimeConfigKey("quarkus.mcp.server.client-logging.default-level", "DEBUG");
        // Disable all input schema generators by default
        config.overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.jackson.enabled", "false");
        config.overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.jakarta-validation.enabled", "false");
        config.overrideRuntimeConfigKey("quarkus.mcp.server.schema-generator.swagger2.enabled", "false");
        return config;
    }

    @BeforeEach
    void setTestUri() {
        McpAssured.baseUri = testUri;
    }

}
