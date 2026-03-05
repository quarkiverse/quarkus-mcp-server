package io.quarkiverse.mcp.server.test.validation.icons;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

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

public class IconsProviderInnerClassTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(InvalidTools.class, InnerIconsProvider.class))
            .assertException(t -> {
                Throwable rootCause = ExceptionUtil.getRootCause(t);
                String rootStr = rootCause.toString();
                assertTrue(rootStr.contains("IconsProvider implementations must not be inner classes:"), rootStr);
            });

    @Test
    public void test() {
        fail();
    }

    public static class InvalidTools {

        @Icons(InnerIconsProvider.class)
        @Tool
        String foo() {
            throw new ToolCallException("boom");
        }

    }

    public class InnerIconsProvider implements IconsProvider {

        @Override
        public List<Icon> get(FeatureInfo feature) {
            return List.of();
        }

    }

}
