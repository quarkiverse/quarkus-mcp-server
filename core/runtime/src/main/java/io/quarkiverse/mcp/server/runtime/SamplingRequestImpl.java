package io.quarkiverse.mcp.server.runtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ModelPreferences;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.SamplingMessage;
import io.quarkiverse.mcp.server.SamplingRequest;
import io.quarkiverse.mcp.server.SamplingResponse;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@JsonInclude(Include.NON_NULL)
public class SamplingRequestImpl implements SamplingRequest {

    private static final Logger LOG = Logger.getLogger(SamplingRequestImpl.class);

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
    private final Duration timeout;

    SamplingRequestImpl(long maxTokens, List<SamplingMessage> messages, BigDecimal temperature, String systemPrompt,
            IncludeContext includeContext, ModelPreferences modelPreferences, Map<String, Object> metadata,
            List<String> stopSequences, Sender sender,
            ResponseHandlers responseHandlers, Duration timeout) {
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
        this.timeout = timeout;
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
        AtomicLong id = new AtomicLong();
        Uni<SamplingResponse> ret = Uni.createFrom().completionStage(() -> {
            CompletableFuture<SamplingResponse> future = new CompletableFuture<SamplingResponse>();
            Long requestId = responseHandlers.newRequest(m -> {
                JsonObject result = m.getJsonObject("result");
                if (result == null) {
                    throw new IllegalStateException("Invalid sampling response: " + m);
                }
                String model = result.getString("model");
                Role role = Role.valueOf(result.getString("role").toUpperCase());
                Content content = Contents.parseContent(result.getJsonObject("content"));
                SamplingResponse samplingResponse = new SamplingResponse(content, model, role, result.getString("stopReason"));
                future.complete(samplingResponse);
            });
            id.set(requestId);
            sender.send(Messages.newRequest(id, McpMessageHandler.SAMPLING_CREATE_MESSAGE, this));
            return future;
        });
        if (!timeout.isNegative() && !timeout.isZero()) {
            ret = ret.ifNoItem()
                    .after(timeout).fail()
                    .onFailure(TimeoutException.class).invoke(te -> {
                        long requestId = id.get();
                        if (requestId != 0 && responseHandlers.remove(requestId)) {
                            LOG.debugf("Response handler for %s removed due to timeout", requestId);
                        }
                    });
        }
        return ret;
    }

}
