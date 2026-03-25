package io.quarkiverse.mcp.server.sse.it;

import java.util.List;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;
import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.Icons;
import io.quarkiverse.mcp.server.IconsProvider;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.smallrye.mutiny.Uni;

public class ServerFeatures {

    @Inject
    CodeService codeService;

    @Icons(ToolIcons.class)
    @Tool
    TextContent toLowerCase(String value) {
        return new TextContent(value.toLowerCase());
    }

    @Prompt(name = "code_assist")
    PromptMessage codeAssist(@PromptArg(name = "lang") String language) {
        return PromptMessage.withUserRole(new TextContent(codeService.assist(language)));
    }

    @Resource(uri = "file:///project/alpha")
    BlobResourceContents alpha(RequestUri uri) {
        return BlobResourceContents.create(uri.value(), "data".getBytes());
    }

    @Tool
    Answer answer(String question) {
        return new Answer(question.toLowerCase());
    }

    public record Answer(String value) {
    }

    public static class ToolIcons implements IconsProvider {

        @Override
        public List<Icon> get(FeatureInfo feature) {
            return List.of(new Icon("file://tool-icon", "image/png"));
        }

    }

    @Tool
    Uni<String> samplingTool(Sampling sampling) {
        if (sampling.isSupported()) {
            SamplingRequest samplingRequest = sampling.requestBuilder()
                    .setMaxTokens(100)
                    .addMessage(SamplingMessage.withUserRole("What's happening?"))
                    .build();
            return samplingRequest.send().map(sr -> sr.content().asText().text());
        } else {
            return Uni.createFrom().item("Sampling not supported");
        }
    }

    @Tool
    TextContent failingTool(String value) {
        throw new RuntimeException("Tool execution failed: " + value);
    }

    @Tool
    Uni<String> elicitationTool(Elicitation elicitation) {
        if (elicitation.isSupported()) {
            ElicitationRequest request = elicitation.requestBuilder()
                    .setMessage("What's your name?")
                    .addSchemaProperty("name", new StringSchema(true))
                    .build();
            return request.send().map(response -> {
                if (response.actionAccepted()) {
                    return "Hello " + response.content().getString("name") + "!";
                } else {
                    return "Not accepted";
                }
            });
        } else {
            return Uni.createFrom().item("Elicitation not supported");
        }
    }

}
