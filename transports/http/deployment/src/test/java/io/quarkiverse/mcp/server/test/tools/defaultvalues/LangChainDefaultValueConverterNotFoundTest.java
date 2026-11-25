package io.quarkiverse.mcp.server.test.tools.defaultvalues;

import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.test.QuarkusExtensionTest;

public class LangChainDefaultValueConverterNotFoundTest {

    @RegisterExtension
    public static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidTools.class);
            })
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void test() {
        fail();
    }

    public static class InvalidTools {

        @Tool
        String alpha(@P(defaultValue = "1") BigDecimal val) {
            return val.toString();
        }

    }

}
