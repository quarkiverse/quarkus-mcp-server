package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Notification.Type;
import io.quarkus.test.QuarkusUnitTest;

public class InitInvalidParamTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InitInvalidParam.class);
            })
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InitInvalidParam {

        @Notification(Type.INITIALIZED)
        void foo(int jo, McpConnection conn) {
        }

    }

}
