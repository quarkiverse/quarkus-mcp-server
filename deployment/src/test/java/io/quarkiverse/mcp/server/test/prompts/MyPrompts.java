package io.quarkiverse.mcp.server.test.prompts;

import static io.quarkiverse.mcp.server.test.Checks.checkDuplicatedContext;
import static io.quarkiverse.mcp.server.test.Checks.checkExecutionModel;
import static io.quarkiverse.mcp.server.test.Checks.checkMcpConnection;
import static io.quarkiverse.mcp.server.test.Checks.checkRequestContext;
import static io.quarkiverse.mcp.server.test.Checks.checkRequestId;

import java.util.List;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.Options;
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
        return List.of(PromptMessage.withUserRole(new TextContent(val.toUpperCase())));
    }

    @Prompt
    Uni<PromptMessage> uni_bar(String val) {
        checkExecutionModel(false);
        checkRequestContext();
        checkDuplicatedContext();
        return Uni.createFrom().item(PromptMessage.withUserRole(new TextContent(val.toUpperCase())));
    }

    @Prompt
    Uni<List<PromptMessage>> uni_list_bar(String val) {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item(List.of(PromptMessage.withUserRole(new TextContent(val.toUpperCase()))));
    }

    @Prompt
    PromptResponse response(String val) {
        checkExecutionModel(true);
        checkRequestContext();
        checkDuplicatedContext();
        return new PromptResponse("My description", List.of(PromptMessage.withUserRole(new TextContent(val.toUpperCase()))));
    }

    @Prompt
    Uni<PromptResponse> uni_response(String val) {
        checkExecutionModel(false);
        checkRequestContext();
        checkDuplicatedContext();
        return Uni.createFrom()
                .item(new PromptResponse("My description",
                        List.of(PromptMessage.withUserRole(new TextContent(val.toUpperCase())))));
    }

}
