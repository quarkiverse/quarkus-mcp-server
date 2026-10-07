package io.quarkiverse.mcp.server.stdio.it;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.WrapBusinessError;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;

/**
 * Verifies that the CDI request context of a tool call is destroyed once the call is done.
 * <p>
 * The stdio server runs in a separate process, so the destruction can't be observed via a static
 * field from the test. Instead, each request scoped bean records its destruction in an application
 * scoped {@link DestructionTracker} that the client reads back through the {@code destroyed_report}
 * tool.
 */
public class RequestContextFeatures {

    @Inject
    PingWorkerBean workerBean;

    @Inject
    PingVirtualBean virtualBean;

    @Inject
    PingReactiveBean reactiveBean;

    @Inject
    PingErrorBean errorBean;

    @Inject
    PingPromptBean promptBean;

    @Inject
    PingResourceBean resourceBean;

    @Inject
    DestructionTracker tracker;

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

    /**
     * Reports how many request scoped beans were destroyed so far, per execution model / feature.
     * Destruction happens after the response of the triggering call is sent, so the client must
     * poll this tool.
     */
    @Tool
    String destroyed_report() {
        return "worker=" + tracker.destroyed("worker")
                + ",virtual=" + tracker.destroyed("virtual")
                + ",reactive=" + tracker.destroyed("reactive")
                + ",error=" + tracker.destroyed("error")
                + ",prompt=" + tracker.destroyed("prompt")
                + ",resource=" + tracker.destroyed("resource");
    }

    @ApplicationScoped
    public static class DestructionTracker {

        private final Map<String, AtomicInteger> destroyed = new ConcurrentHashMap<>();

        void recordDestroyed(String model) {
            destroyed.computeIfAbsent(model, k -> new AtomicInteger()).incrementAndGet();
        }

        int destroyed(String model) {
            AtomicInteger count = destroyed.get(model);
            return count == null ? 0 : count.get();
        }
    }

    @RequestScoped
    public static class PingWorkerBean {

        @Inject
        DestructionTracker tracker;

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            tracker.recordDestroyed("worker");
        }
    }

    @RequestScoped
    public static class PingVirtualBean {

        @Inject
        DestructionTracker tracker;

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            tracker.recordDestroyed("virtual");
        }
    }

    @RequestScoped
    public static class PingReactiveBean {

        @Inject
        DestructionTracker tracker;

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            tracker.recordDestroyed("reactive");
        }
    }

    @RequestScoped
    public static class PingErrorBean {

        @Inject
        DestructionTracker tracker;

        void touch() {
        }

        @PreDestroy
        void destroy() {
            tracker.recordDestroyed("error");
        }
    }

    @RequestScoped
    public static class PingPromptBean {

        @Inject
        DestructionTracker tracker;

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            tracker.recordDestroyed("prompt");
        }
    }

    @RequestScoped
    public static class PingResourceBean {

        @Inject
        DestructionTracker tracker;

        String ping() {
            return "pong";
        }

        @PreDestroy
        void destroy() {
            tracker.recordDestroyed("resource");
        }
    }
}
