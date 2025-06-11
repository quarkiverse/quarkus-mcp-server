package io.quarkiverse.mcp.server.test.init;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.InitialCheck;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class InitCheckPrioritySuccessTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(AlphaCheck.class, BravoCheck.class));

    @Test
    public void testInitRequest() throws InterruptedException {
        initClient();
        assertEquals(BravoCheck.class.getSimpleName(), AlphaCheck.CHECKS.get(0));
        assertEquals(AlphaCheck.class.getSimpleName(), AlphaCheck.CHECKS.get(1));
    }

    @Singleton
    public static class AlphaCheck implements InitialCheck {

        static final List<String> CHECKS = new CopyOnWriteArrayList<>();

        @Override
        public Uni<CheckResult> perform(InitialRequest initialRequest) {
            CHECKS.add(getClass().getSimpleName());
            return InitialCheck.CheckResult.successs();
        }

    }

    @Priority(5) // this check should be applied first
    @Singleton
    public static class BravoCheck implements InitialCheck {

        @Override
        public Uni<CheckResult> perform(InitialRequest initialRequest) {
            AlphaCheck.CHECKS.add(getClass().getSimpleName());
            return InitialCheck.CheckResult.successs();
        }

    }

}
