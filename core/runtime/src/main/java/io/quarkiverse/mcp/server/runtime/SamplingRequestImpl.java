package io.quarkiverse.mcp.server.runtime;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.ModelPreferences;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.SamplingResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@JsonInclude(Include.NON_NULL)
public class SamplingRequestImpl implements SamplingRequest {

    private final long maxTokens;
    private final List<SamplingMessage> messages;
    private final BigDecimal temperature;
    private final String systemPrompt;
    private final IncludeContext includeContext;
    private final ModelPreferences modelPreferences;
    private final Map<String, Object> metadata;
    private final List<String> stopSequences;

    private final Sender sender;
    private final ResponseHandlers responseHandlers;

    SamplingRequestImpl(long maxTokens, List<SamplingMessage> messages, BigDecimal temperature, String systemPrompt,
            IncludeContext includeContext,
            ModelPreferences modelPreferences, Map<String, Object> metadata, List<String> stopSequences, Sender sender,
            ResponseHandlers responseHandlers) {
        this.maxTokens = maxTokens;
        this.messages = messages;
        this.temperature = temperature;
        this.systemPrompt = systemPrompt;
        this.includeContext = includeContext;
        this.modelPreferences = modelPreferences;
        this.metadata = metadata;
        this.stopSequences = stopSequences;
        this.sender = sender;
        this.responseHandlers = responseHandlers;
    }

    @JsonProperty
    @Override
    public long maxTokens() {
        return maxTokens;
    }

    @JsonProperty
    @Override
    public List<SamplingMessage> messages() {
        return messages;
    }

    @JsonProperty
    @Override
    public List<String> stopSequences() {
        return stopSequences;
    }

    @JsonProperty
    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @JsonProperty
    @Override
    public BigDecimal temperature() {
        return temperature;
    }

    @JsonProperty
    @Override
    public IncludeContext includeContext() {
        return includeContext;
    }

    @JsonProperty
    @Override
    public ModelPreferences modelPreferences() {
        return modelPreferences;
    }

    @JsonProperty
    @Override
    public Map<String, Object> metadata() {
        return metadata;
    }

    @Override
    public Uni<SamplingResponse> send() {
        return Uni.createFrom().completionStage(() -> {
            CompletableFuture<SamplingResponse> ret = new CompletableFuture<SamplingResponse>();
            Long id = responseHandlers.newRequest(m -> {
                JsonObject result = m.getJsonObject("result");
                if (result == null) {
                    throw new IllegalStateException("Invalid sampling response: " + m);
                }
                String model = result.getString("model");
                Role role = Role.valueOf(result.getString("role").toUpperCase());
                JsonObject content = result.getJsonObject("content");
                Content.Type contentType = Content.Type.valueOf(content.getString("type").toUpperCase());
                Content c = switch (contentType) {
                    case TEXT -> content.mapTo(TextContent.class);
                    case IMAGE -> content.mapTo(ImageContent.class);
                    default -> throw new IllegalArgumentException("Unexpected value: " + contentType);
                };
                SamplingResponse samplingResponse = new SamplingResponse(c, model, role, result.getString("stopReason"));
                ret.complete(samplingResponse);
            });
            sender.send(Messages.newRequest(id, McpMessageHandler.SAMPLING_CREATE_MESSAGE, this));
            return ret;
        });
    }

}
