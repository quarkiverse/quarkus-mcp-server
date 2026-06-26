package io.quarkiverse.mcp.server.test.mcpjava;

import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.progress.ProgressToken;
import org.mcpjava.server.progress.ProgressTracker;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

public class McpJavaProgressFeatures {

    @Tool(description = "Tool with progress notification")
    String progressNotification(@ToolArg(description = "A value") String val, Progress progress) {
        progress.notificationBuilder()
                .setProgress(1)
                .setTotal(5)
                .setMessage("step 1")
                .build()
                .sendAndForget();
        return "notification:" + val;
    }

    @Tool(description = "Tool with progress tracker")
    String progressTracker(@ToolArg(description = "A value") String val, Progress progress) {
        ProgressTracker tracker = progress.trackerBuilder()
                .setTotal(3)
                .setDefaultStep(1)
                .setMessageBuilder(p -> "progress: " + p)
                .build();
        tracker.advanceAndForget();
        tracker.advanceAndForget();
        return "tracker:" + val;
    }

    @Prompt(description = "Prompt with progress")
    String promptWithProgress(@PromptArg(description = "Name") String name, Progress progress) {
        progress.notificationBuilder()
                .setProgress(1)
                .setTotal(1)
                .build()
                .sendAndForget();
        return "Hello " + name + "!";
    }

    @Tool(description = "Tool that accesses progress token")
    String progressTokenInfo(@ToolArg(description = "A value") String val, Progress progress) {
        if (progress.token().isPresent()) {
            ProgressToken token = progress.token().get();
            if (token.type() == ProgressToken.Type.INTEGER) {
                return "token:INTEGER:" + token.asInteger();
            }
            return "token:STRING:" + token.asString();
        }
        return "token:absent";
    }
}
