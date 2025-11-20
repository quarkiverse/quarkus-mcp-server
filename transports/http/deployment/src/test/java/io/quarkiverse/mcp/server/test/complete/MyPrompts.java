package io.quarkiverse.mcp.server.test.complete;

import java.util.List;

import io.quarkiverse.mcp.server.CompleteArg;
import io.quarkiverse.mcp.server.CompletePrompt;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkus.logging.Log;

public class MyPrompts {

    static final List<String> NAMES = List.of("Martin", "Lu", "Jachym", "Vojtik", "Onda");

    @Prompt(description = "Not much we can say here.")
    PromptMessage foo(@PromptArg(description = "The name") String name, String suffix) {
        return PromptMessage.withUserRole(new TextContent(name.toLowerCase() + suffix));
    }

    @CompletePrompt("foo")
    List<String> completeName(@CompleteArg(name = "name") String val) {
        Log.infof("Complete name: %s", val);
        return NAMES.stream().filter(n -> n.startsWith(val)).toList();
    }

    @CompletePrompt("foo")
    String completeSuffix(String suffix) {
        Log.infof("Complete suffix: %s", suffix);
        return "_foo";
    }
}
