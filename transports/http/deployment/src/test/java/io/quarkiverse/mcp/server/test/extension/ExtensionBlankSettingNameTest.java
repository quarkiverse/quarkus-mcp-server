package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionSetting;
import io.quarkus.test.QuarkusUnitTest;

public class ExtensionBlankSettingNameTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(BadExtension.class))
            // A setting name must not be blank
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    @McpExtension(id = "com.acme/blank-setting")
    @McpExtensionSetting(name = "  ", value = "x")
    public static class BadExtension {
    }

}
