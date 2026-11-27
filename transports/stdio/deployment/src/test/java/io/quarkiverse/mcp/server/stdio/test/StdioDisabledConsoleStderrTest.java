package io.quarkiverse.mcp.server.stdio.test;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

public class StdioDisabledConsoleStderrTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .overrideRuntimeConfigKey("quarkus.mcp.server.stdio.enabled", "false");

    @Test
    public void testConsoleLoggingNotRedirected() {
        assertFalse(ConfigProvider.getConfig().getValue("quarkus.log.console.stderr", Boolean.class));
    }

}
