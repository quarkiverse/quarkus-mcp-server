package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class ResourceInvalidParamTest {

    @RegisterExtension
    public static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidParam.class);
            })
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidParam {

        @Resource(uri = "fooo")
        ResourceResponse foo(int jo, boolean ne) {
            return null;
        }

    }

}
