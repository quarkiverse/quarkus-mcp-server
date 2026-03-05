package io.quarkiverse.mcp.server.test.validation.icons;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.Icons;
import io.quarkiverse.mcp.server.IconsProvider;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.runtime.util.ExceptionUtil;
import io.quarkus.test.QuarkusUnitTest;

public class IconsProviderAmbiguousBeansTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(InvalidTools.class, Alpha.class, Bravo.class);
            })
            .assertException(t -> {
                Throwable rootCause = ExceptionUtil.getRootCause(t);
                String rootStr = rootCause.toString();
                assertTrue(rootStr.contains(" There must be exactly one bean that matches"), rootStr);
            });

    @Test
    public void test() {
        fail();
    }

    public static class InvalidTools {

        @Icons(Alpha.class)
        @Tool
        String foo() {
            throw new ToolCallException("boom");
        }

    }

    @Singleton
    public static class Alpha implements IconsProvider {

        @Override
        public List<Icon> get(FeatureInfo feature) {
            return null;
        }

    }

    @Singleton
    public static class Bravo extends Alpha {

    }

}
