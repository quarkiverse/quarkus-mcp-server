package io.quarkiverse.mcp.server.test.prompts;

import java.util.List;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpConnection.Status;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.Options;
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
    List<PromptMessage> bar(String val, RequestId id, McpConnection connection) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        checkRequestId(id);
        checkMcpConnection(connection);
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

    @Prompt
    PromptResponse response(String val) {
        checkExecutionModel(true);
        checkRequestContext();
        checkDuplicatedContext();
        return new PromptResponse("My description", List.of(PromptMessage.user(new TextContent(val.toUpperCase()))));
    }

    @Prompt
    Uni<PromptResponse> uni_response(String val) {
        checkExecutionModel(false);
        checkRequestContext();
        checkDuplicatedContext();
        return Uni.createFrom()
                .item(new PromptResponse("My description", List.of(PromptMessage.user(new TextContent(val.toUpperCase())))));
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

    private void checkRequestId(RequestId id) {
        if (id == null || id.asInteger() < 1) {
            throw new IllegalStateException("Invalid request id: " + id);
        }
    }

    private void checkMcpConnection(McpConnection connection) {
        if (connection == null || connection.status() != Status.IN_OPERATION) {
            throw new IllegalStateException("Invalid connection: " + connection);
        }
    }

}
