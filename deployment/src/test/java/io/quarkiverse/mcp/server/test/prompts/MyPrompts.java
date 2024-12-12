package io.quarkiverse.mcp.server.test.prompts;

import java.util.List;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.BlockingOperationControl;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;

public class MyPrompts {

    @Inject
    FooService fooService;

    @Prompt(description = "Not much we can say here.")
    PromptMessage foo(@PromptArg(description = "The name") String name, int repeat, Options options) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return new PromptMessage("user", new TextContent(fooService.ping(name, repeat, options)));
    }

    @Prompt(name = "BAR")
    List<PromptMessage> bar(String val) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return List.of(PromptMessage.user(new TextContent(val.toUpperCase())));
    }

    @Prompt
    Uni<PromptMessage> uni_bar(String val) {
        checkExecutionModel(false);
        checkRequestContext();
        checkDuplicatedContext();
        return Uni.createFrom().item(PromptMessage.user(new TextContent(val.toUpperCase())));
    }

    @Prompt
    Uni<List<PromptMessage>> uni_list_bar(String val) {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item(List.of(PromptMessage.user(new TextContent(val.toUpperCase()))));
    }

    private void checkRequestContext() {
        if (!Arc.container().requestContext().isActive()) {
            throw new IllegalStateException("Request context not active");
        }
    }

    private void checkExecutionModel(boolean blocking) {
        if (BlockingOperationControl.isBlockingAllowed() && !blocking) {
            throw new IllegalStateException("Invalid execution model");
        }
    }

    private void checkDuplicatedContext() {
        if (!VertxContext.isOnDuplicatedContext()) {
            throw new IllegalStateException("Not on duplicated context");
        }
    }

    public record Options(boolean enabled) {
    }

}
