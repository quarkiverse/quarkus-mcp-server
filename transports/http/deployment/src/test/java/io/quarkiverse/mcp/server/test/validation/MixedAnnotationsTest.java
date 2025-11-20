package io.quarkiverse.mcp.server.test.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.runtime.util.ExceptionUtil;
import io.quarkus.test.QuarkusUnitTest;

public class MixedAnnotationsTest {

    @RegisterExtension
    public static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> {
                root.addClasses(MixedAnnotations.class);
            })
            .assertException(t -> {
                Throwable rootCause = ExceptionUtil.getRootCause(t);
                assertEquals(
                        "Parameter of a TOOL method may not be annotated with @PromptArg: MixedAnnotationsTest$MixedAnnotations#bar(java.lang.String)",
                        rootCause.getMessage());
            });

    @Test
    public void test() {
        fail();
    }

    public static class MixedAnnotations {

        @Tool
        String bar(@PromptArg String name) {
            return null;
        }

    }

}
