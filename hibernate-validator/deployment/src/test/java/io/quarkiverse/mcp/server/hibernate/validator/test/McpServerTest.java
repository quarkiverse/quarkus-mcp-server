package io.quarkiverse.mcp.server.hibernate.validator.test;

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
        if (System.getProperty("logTraffic") != null) {
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.enabled", "true");
            config.overrideConfigKey("quarkus.mcp.server.traffic-logging.text-limit", "" + textLimit);
        }
        return config;
    }

    @BeforeEach
    void setTestUri() {
        McpAssured.baseUri = testUri;
    }

}
