package io.quarkiverse.mcp.server.runtime;

import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.PromptResponseEncoder;

@Singleton
public class PromptEncoderResultMapper extends EncoderResultMapper<PromptResponse, PromptResponseEncoder<?>, PromptResponse> {

    @Override
    protected PromptResponse toResponse(PromptResponse encoded) {
        return encoded;
    }

}
