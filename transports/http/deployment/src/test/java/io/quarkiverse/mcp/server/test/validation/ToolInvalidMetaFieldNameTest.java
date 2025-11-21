package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.MetaField;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.test.QuarkusUnitTest;

public class ToolInvalidMetaFieldNameTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidMetaField.class);
            })
            .setExpectedException(IllegalArgumentException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidMetaField {

        @MetaField(name = "_foo", value = "nok")
        @Tool
        String foo() {
            throw new ToolCallException("boom");
        }

    }

}
