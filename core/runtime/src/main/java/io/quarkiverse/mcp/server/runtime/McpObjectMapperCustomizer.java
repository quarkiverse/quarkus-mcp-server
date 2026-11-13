package io.quarkiverse.mcp.server.runtime;

import jakarta.enterprise.context.Dependent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.CacheControl;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceLink;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import tools.jackson.databind.json.JsonMapper;

@Dependent
public class McpObjectMapperCustomizer implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.addMixIn(ToolResponse.class, ResponseMixin.class);
        builder.addMixIn(PromptResponse.class, ResponseMixin.class);
        builder.addMixIn(ResourceResponse.class, ResourceResponseMixin.class);
        builder.addMixIn(CacheControl.class, ResponseMixin.class);
        builder.addMixIn(CompletionResponse.class, ResponseMixin.class);

        builder.addMixIn(TextResourceContents.class, ResponseMixin.class);
        builder.addMixIn(BlobResourceContents.class, ResponseMixin.class);

        builder.addMixIn(AudioContent.class, ResponseMixin.class);
        builder.addMixIn(EmbeddedResource.class, ResponseMixin.class);
        builder.addMixIn(ImageContent.class, ResponseMixin.class);
        builder.addMixIn(ResourceLink.class, ResponseMixin.class);
        builder.addMixIn(TextContent.class, ResponseMixin.class);
        builder.addMixIn(Content.Annotations.class, ResponseMixin.class);

        builder.addMixIn(Icon.class, ResponseMixin.class);
        builder.addMixIn(Implementation.class, ResponseMixin.class);
    }

    @JsonInclude(Include.NON_NULL)
    static abstract class ResponseMixin {

    }

    @JsonInclude(Include.NON_NULL)
    public static abstract class ResourceResponseMixin {
        // The cache control hints are serialized as flat top-level fields (ttlMs/cacheScope) by
        // ResourceMessageHandler#resourcesRead; the record property itself must never be serialized.
        // A method-level annotation is used intentionally: it needs only this mixin registered for
        // reflection, whereas a field-level one also needs ResourceResponse.fields() and the
        // CacheControl/CacheScope types (see McpServerProcessor#registerForReflection).
        @JsonIgnore
        abstract CacheControl cacheControl();
    }

}
