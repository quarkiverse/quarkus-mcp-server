package io.quarkiverse.mcp.server.test;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

public abstract class McpServerTest {

    @TestHTTPResource
    protected URI testUri;

    public static QuarkusUnitTest defaultConfig() {
        return defaultConfig(500);
    }

    public static QuarkusUnitTest defaultConfig(int textLimit) {
        // TODO in theory, we should also add SseClient to all test archives
        // but the test CL can see the class and we don't need Quarkus to analyze this util class
        QuarkusUnitTest config = new QuarkusUnitTest();
        if (System.getProperty("logTraffic") != null) {
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.enabled", "true");
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.text-limit", "" + textLimit);
        }
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
