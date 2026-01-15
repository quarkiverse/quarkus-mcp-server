package io.quarkiverse.mcp.server.runtime;

import jakarta.enterprise.context.Dependent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceLink;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.jackson.ObjectMapperCustomizer;

@Dependent
public class McpObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.addMixIn(ToolResponse.class, ResponseMixin.class);
        objectMapper.addMixIn(PromptResponse.class, ResponseMixin.class);
        objectMapper.addMixIn(ResourceResponse.class, ResponseMixin.class);
        objectMapper.addMixIn(CompletionResponse.class, ResponseMixin.class);

        objectMapper.addMixIn(TextResourceContents.class, ResponseMixin.class);
        objectMapper.addMixIn(BlobResourceContents.class, ResponseMixin.class);

        objectMapper.addMixIn(AudioContent.class, ResponseMixin.class);
        objectMapper.addMixIn(EmbeddedResource.class, ResponseMixin.class);
        objectMapper.addMixIn(ImageContent.class, ResponseMixin.class);
        objectMapper.addMixIn(ResourceLink.class, ResponseMixin.class);
        objectMapper.addMixIn(TextContent.class, ResponseMixin.class);

        objectMapper.addMixIn(Icon.class, ResponseMixin.class);

    }

    @JsonInclude(Include.NON_NULL)
    static abstract class ResponseMixin {

    }

}
