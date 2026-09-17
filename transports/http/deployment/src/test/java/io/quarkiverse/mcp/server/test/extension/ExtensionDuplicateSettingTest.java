package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionSetting;
import io.quarkiverse.mcp.server.MetaField.Type;
import io.quarkus.test.QuarkusUnitTest;

public class ExtensionDuplicateSettingTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(BadExtension.class))
            // Two settings declare the same name within a single extension
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    @McpExtension(id = "com.acme/dup-setting")
    @McpExtensionSetting(name = "directoryRead", type = Type.BOOLEAN, value = "true")
    @McpExtensionSetting(name = "directoryRead", type = Type.BOOLEAN, value = "false")
    public static class BadExtension {
    }

}
