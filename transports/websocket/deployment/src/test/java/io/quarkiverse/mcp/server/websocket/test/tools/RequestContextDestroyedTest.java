package io.quarkiverse.mcp.server.websocket.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpWebSocketTestClient;
import io.quarkiverse.mcp.server.websocket.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;

public class RequestContextDestroyedTest extends McpServerTest {

    static final int CALL_COUNT = 3;

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(Features.class,
                            WorkerBean.class, VirtualBean.class, ReactiveBean.class,
                            PromptBean.class, ResourceBean.class, ErrorBean.class));

    @Test
    public void testRequestContextDestroyed() throws InterruptedException {
        McpWebSocketTestClient client = McpAssured.newConnectedWebSocketClient();

        // --- tools: three execution models, CALL_COUNT times each ---
        for (int i = 0; i < CALL_COUNT; i++) {
            client.when()
                    .toolsCall("ping_worker", r -> assertEquals("pong", r.firstContent().asText().text()))
                    .toolsCall("ping_virtual", r -> assertEquals("pong", r.firstContent().asText().text()))
                    .toolsCall("ping_reactive", r -> assertEquals("pong", r.firstContent().asText().text()))
                    .thenAssertResults();
        }

        // --- tool: error path (thrown exception) ---
        client.when()
                .toolsCall("ping_error", r -> assertTrue(r.isError(),
                        "Expected isError=true for a throwing tool"))
                .thenAssertResults();

        // --- prompt: request context must be destroyed after prompts/get ---
        client.when()
                .promptsGet("ping_prompt", r -> assertEquals("pong",
                        r.messages().get(0).content().asText().text()))
                .thenAssertResults();

        // --- resource: request context must be destroyed after resources/read ---
        client.when()
                .resourcesRead("file:///ping", r -> assertEquals("pong",
                        r.contents().get(0).asText().text()))
                .thenAssertResults();

        // The @RequestScoped bean used during a tool call must be destroyed once the request context
        // associated with the call is terminated - regardless of the execution model
        assertTrue(WorkerBean.DESTROYED.await(10, TimeUnit.SECONDS),
                "Request scoped bean was not destroyed after the worker thread tool call");
        assertTrue(VirtualBean.DESTROYED.await(10, TimeUnit.SECONDS),
                "Request scoped bean was not destroyed after the virtual thread tool call");
        assertTrue(ReactiveBean.DESTROYED.await(10, TimeUnit.SECONDS),
                "Request scoped bean was not destroyed after the event loop tool call");

        // Each of the CALL_COUNT calls must have destroyed its own bean instance
        assertEquals(0, WorkerBean.DESTROYED.getCount());
        assertEquals(0, VirtualBean.DESTROYED.getCount());
        assertEquals(0, ReactiveBean.DESTROYED.getCount());

        // Error path: bean must be destroyed even when the tool throws
        assertTrue(ErrorBean.DESTROYED.await(10, TimeUnit.SECONDS),
                "Request scoped bean was not destroyed after the throwing tool call");

        // prompts/get and resources/read go through the same operation() path
        assertTrue(PromptBean.DESTROYED.await(10, TimeUnit.SECONDS),
                "Request scoped bean was not destroyed after the prompts/get call");
        assertTrue(ResourceBean.DESTROYED.await(10, TimeUnit.SECONDS),
                "Request scoped bean was not destroyed after the resources/read call");
    }

    public static class Features {

        @Inject
        WorkerBean workerBean;

        @Inject
        VirtualBean virtualBean;

        @Inject
        ReactiveBean reactiveBean;

        @Inject
        ErrorBean errorBean;

        @Inject
        PromptBean promptBean;

        @Inject
        ResourceBean resourceBean;

        @Tool
        String ping_worker() {
            return workerBean.ping();
        }

        @RunOnVirtualThread
        @Tool
        String ping_virtual() {
            return virtualBean.ping();
        }

        @Tool
        Uni<String> ping_reactive() {
            return Uni.createFrom().item(reactiveBean.ping());
        }

        @WrapBusinessError
        @Tool
        String ping_error() {
            errorBean.touch();
            throw new RuntimeException("intentional error");
        }

        @Prompt
        PromptMessage ping_prompt() {
            return PromptMessage.withUserRole(new TextContent(promptBean.ping()));
        }

        @Resource(uri = "file:///ping")
        TextResourceContents ping_resource() {
            return new TextResourceContents("file:///ping", resourceBean.ping(), null);
        }

    }

    @RequestScoped
    public static class WorkerBean {

        static final CountDownLatch DESTROYED = new CountDownLatch(CALL_COUNT);

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            DESTROYED.countDown();
        }

    }

    @RequestScoped
    public static class VirtualBean {

        static final CountDownLatch DESTROYED = new CountDownLatch(CALL_COUNT);

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            DESTROYED.countDown();
        }

    }

    @RequestScoped
    public static class ReactiveBean {

        static final CountDownLatch DESTROYED = new CountDownLatch(CALL_COUNT);

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            DESTROYED.countDown();
        }

    }

    @RequestScoped
    public static class ErrorBean {

        static final CountDownLatch DESTROYED = new CountDownLatch(1);

        void touch() {
        }

        @PreDestroy
        void destroy() {
            DESTROYED.countDown();
        }

    }

    @RequestScoped
    public static class PromptBean {

        static final CountDownLatch DESTROYED = new CountDownLatch(1);

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            DESTROYED.countDown();
        }

    }

    @RequestScoped
    public static class ResourceBean {

        static final CountDownLatch DESTROYED = new CountDownLatch(1);

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            DESTROYED.countDown();
        }

    }

}
