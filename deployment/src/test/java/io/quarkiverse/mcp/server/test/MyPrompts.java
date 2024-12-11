package io.quarkiverse.mcp.server.test;

import java.util.List;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkus.logging.Log;

public class MyPrompts {

    @Inject
    FooService fooService;

    @Prompt(description = "Not much we can say here.")
    PromptMessage foo(@PromptArg(description = "The name") String name, int repeat, Options options) {
        Log.infof("Invoked foo prompt...");
        return new PromptMessage("user", new TextContent(fooService.ping(name, repeat, options)));
    }

    @Prompt(name = "BAR")
    List<PromptMessage> bar(String val) {
        return List.of(PromptMessage.user(new TextContent(val.toUpperCase())));
    }

    public record Options(boolean enabled) {
    }

}
