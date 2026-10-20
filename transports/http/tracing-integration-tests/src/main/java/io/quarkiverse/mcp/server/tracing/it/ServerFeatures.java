package io.quarkiverse.mcp.server.tracing.it;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationRequest.StringSchema;
import io.quarkiverse.mcp.server.ElicitationResponse;
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

    @Tool
    Uni<String> samplingTool(Sampling sampling) {
        if (sampling.isSupported()) {
            if (sampling.inputResponses().has("llmInput")) {
                return Uni.createFrom()
                        .item(sampling.inputResponses().getSamplingResponse("llmInput").content().asText().text());
            }
            SamplingRequest samplingRequest = sampling.requestBuilder()
                    .setMaxTokens(100)
                    .addMessage(SamplingMessage.withUserRole("What's happening?"))
                    .build();
            throw sampling.inputRequired().addSamplingRequest("llmInput", samplingRequest).build();
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
        if (elicitation.isFormModeSupported()) {
            if (elicitation.inputResponses().has("userInput")) {
                ElicitationResponse response = elicitation.inputResponses().getElicitationResponse("userInput");
                return Uni.createFrom().item(response.actionAccepted() ? "Hello " + response.content().getString("name") + "!"
                        : "Not accepted");
            }
            ElicitationRequest request = elicitation.requestBuilder()
                    .setMessage("What's your name?")
                    .addSchemaProperty("name", new StringSchema(true))
                    .build();
            throw elicitation.inputRequired().addElicitationRequest("userInput", request).build();
        } else {
            return Uni.createFrom().item("Elicitation not supported");
        }
    }

}
