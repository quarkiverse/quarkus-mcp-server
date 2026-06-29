package io.quarkiverse.mcp.server.runtime.mcpjava;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.mcpjava.server.Icon;
import org.mcpjava.server.Role;
import org.mcpjava.server.completion.CompletionResult;
import org.mcpjava.server.content.Annotations;
import org.mcpjava.server.content.AudioContent;
import org.mcpjava.server.content.ContentBlock;
import org.mcpjava.server.content.EmbeddedResource;
import org.mcpjava.server.content.ImageContent;
import org.mcpjava.server.content.ResourceLink;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.PromptMessage;
import org.mcpjava.server.prompts.PromptResponse;
import org.mcpjava.server.resources.BlobResourceContents;
import org.mcpjava.server.resources.ResourceContents;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.resources.TextResourceContents;
import org.mcpjava.server.spi.McpServerSPI;
import org.mcpjava.server.tools.ToolResponse;

public class QuarkusMcpServerSPI implements McpServerSPI {

    // --- CompletionResult ---

    @Override
    public CompletionResult.Builder completeResultBuilder() {
        return new CompletionResultBuilderImpl();
    }

    record CompletionResultRecord(List<String> values, OptionalInt total, Optional<Boolean> hasMore,
            Map<String, Object> metadata) implements CompletionResult {
    }

    static final class CompletionResultBuilderImpl implements CompletionResult.Builder {
        private final List<String> values = new ArrayList<>();
        private Integer total;
        private Boolean hasMore;
        private Map<String, Object> metadata;

        @Override
        public CompletionResult.Builder addValue(String value) {
            values.add(value);
            return this;
        }

        @Override
        public CompletionResult.Builder addValues(Collection<String> values) {
            this.values.addAll(values);
            return this;
        }

        @Override
        public CompletionResult.Builder setTotal(int total) {
            this.total = total;
            return this;
        }

        @Override
        public CompletionResult.Builder setHasMore(Boolean hasMore) {
            this.hasMore = hasMore;
            return this;
        }

        @Override
        public CompletionResult.Builder putMetadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        @Override
        public CompletionResult.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public CompletionResult build() {
            return new CompletionResultRecord(
                    List.copyOf(values),
                    total != null ? OptionalInt.of(total) : OptionalInt.empty(),
                    Optional.ofNullable(hasMore),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- TextContent ---

    @Override
    public TextContent.Builder textContentBuilder(String text) {
        return new TextContentBuilderImpl(text);
    }

    record TextContentRecord(String text, Optional<Annotations> annotations,
            Map<String, Object> metadata) implements TextContent {
    }

    static final class TextContentBuilderImpl implements TextContent.Builder {
        private final String text;
        private Annotations annotations;
        private Map<String, Object> metadata;

        TextContentBuilderImpl(String text) {
            this.text = text;
        }

        @Override
        public TextContent.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public TextContent.Builder putMetadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        @Override
        public TextContent.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public TextContent build() {
            return new TextContentRecord(text, Optional.ofNullable(annotations),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- ImageContent ---

    @Override
    public ImageContent.Builder imageContentBuilder(byte[] data, String mimeType) {
        return new ImageContentBuilderImpl(data, mimeType);
    }

    record ImageContentRecord(byte[] data, String mimeType, Optional<Annotations> annotations,
            Map<String, Object> metadata) implements ImageContent {
    }

    static final class ImageContentBuilderImpl implements ImageContent.Builder {
        private final byte[] data;
        private final String mimeType;
        private Annotations annotations;
        private Map<String, Object> metadata;

        ImageContentBuilderImpl(byte[] data, String mimeType) {
            this.data = data;
            this.mimeType = mimeType;
        }

        @Override
        public ImageContent.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public ImageContent.Builder putMetadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        @Override
        public ImageContent.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public ImageContent build() {
            return new ImageContentRecord(data, mimeType, Optional.ofNullable(annotations),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- AudioContent ---

    @Override
    public AudioContent.Builder audioContentBuilder(byte[] data, String mimeType) {
        return new AudioContentBuilderImpl(data, mimeType);
    }

    record AudioContentRecord(byte[] data, String mimeType, Optional<Annotations> annotations,
            Map<String, Object> metadata) implements AudioContent {
    }

    static final class AudioContentBuilderImpl implements AudioContent.Builder {
        private final byte[] data;
        private final String mimeType;
        private Annotations annotations;
        private Map<String, Object> metadata;

        AudioContentBuilderImpl(byte[] data, String mimeType) {
            this.data = data;
            this.mimeType = mimeType;
        }

        @Override
        public AudioContent.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public AudioContent.Builder putMetadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        @Override
        public AudioContent.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public AudioContent build() {
            return new AudioContentRecord(data, mimeType, Optional.ofNullable(annotations),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- EmbeddedResource ---

    @Override
    public EmbeddedResource.Builder textEmbeddedResourceBuilder(String text, String uri) {
        return new EmbeddedResourceBuilderImpl(
                new TextResourceContentsBuilderImpl(uri, text));
    }

    @Override
    public EmbeddedResource.Builder blobEmbeddedResourceBuilder(byte[] data, String uri) {
        return new EmbeddedResourceBuilderImpl(
                new BlobResourceContentsBuilderImpl(uri, data));
    }

    record EmbeddedResourceRecord(ResourceContents resource, Optional<Annotations> annotations,
            Map<String, Object> metadata) implements EmbeddedResource {
    }

    static final class EmbeddedResourceBuilderImpl implements EmbeddedResource.Builder {
        private final ResourceContentsBuilderBase<?> resourceBuilder;
        private Annotations annotations;
        private Map<String, Object> metadata;

        EmbeddedResourceBuilderImpl(ResourceContentsBuilderBase<?> resourceBuilder) {
            this.resourceBuilder = resourceBuilder;
        }

        @Override
        public EmbeddedResource.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public EmbeddedResource.Builder setMimeType(String mimeType) {
            resourceBuilder.setMimeTypeInternal(mimeType);
            return this;
        }

        @Override
        public EmbeddedResource.Builder putResourceMeta(String key, Object value) {
            resourceBuilder.putMetadataInternal(key, value);
            return this;
        }

        @Override
        public EmbeddedResource.Builder putMetadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        @Override
        public EmbeddedResource.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public EmbeddedResource build() {
            return new EmbeddedResourceRecord(resourceBuilder.buildInternal(),
                    Optional.ofNullable(annotations),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- ResourceLink ---

    @Override
    public ResourceLink.Builder resourceLinkBuilder(String name, String uri) {
        return new ResourceLinkBuilderImpl(name, uri);
    }

    record ResourceLinkRecord(String name, String title, String uri, Optional<String> description,
            Optional<String> mimeType, Optional<Annotations> annotations, OptionalLong size,
            Map<String, Object> metadata) implements ResourceLink {
    }

    static final class ResourceLinkBuilderImpl implements ResourceLink.Builder {
        private final String name;
        private final String uri;
        private String title;
        private String description;
        private String mimeType;
        private Annotations annotations;
        private Long size;
        private Map<String, Object> metadata;

        ResourceLinkBuilderImpl(String name, String uri) {
            this.name = name;
            this.uri = uri;
            this.title = name;
        }

        @Override
        public ResourceLink.Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        @Override
        public ResourceLink.Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public ResourceLink.Builder setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public ResourceLink.Builder setAnnotations(Annotations annotations) {
            this.annotations = annotations;
            return this;
        }

        @Override
        public ResourceLink.Builder setSize(long size) {
            this.size = size;
            return this;
        }

        @Override
        public ResourceLink.Builder putMetadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        @Override
        public ResourceLink.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public ResourceLink build() {
            return new ResourceLinkRecord(name, title, uri,
                    Optional.ofNullable(description), Optional.ofNullable(mimeType),
                    Optional.ofNullable(annotations),
                    size != null ? OptionalLong.of(size) : OptionalLong.empty(),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- Annotations ---

    @Override
    public Annotations.Builder annotationsBuilder() {
        return new AnnotationsBuilderImpl();
    }

    record AnnotationsRecord(Optional<Set<Role>> audience, OptionalDouble priority,
            Optional<Instant> lastModified) implements Annotations {
    }

    static final class AnnotationsBuilderImpl implements Annotations.Builder {
        private Set<Role> audience;
        private Double priority;
        private Instant lastModified;

        @Override
        public Annotations.Builder setAudience(Role... roles) {
            audience = new LinkedHashSet<>(List.of(roles));
            return this;
        }

        @Override
        public Annotations.Builder setAudience(Set<Role> roles) {
            audience = new LinkedHashSet<>(roles);
            return this;
        }

        @Override
        public Annotations.Builder setPriority(double priority) {
            this.priority = priority;
            return this;
        }

        @Override
        public Annotations.Builder setLastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        @Override
        public Annotations build() {
            return new AnnotationsRecord(
                    audience != null ? Optional.of(Set.copyOf(audience)) : Optional.empty(),
                    priority != null ? OptionalDouble.of(priority) : OptionalDouble.empty(),
                    Optional.ofNullable(lastModified));
        }
    }

    // --- ToolResponse ---

    @Override
    public ToolResponse.Builder toolResponseBuilder() {
        return new ToolResponseBuilderImpl();
    }

    record ToolResponseRecord(List<ContentBlock> content, Optional<Object> structuredContent,
            boolean isError, Map<String, Object> metadata) implements ToolResponse {
    }

    static final class ToolResponseBuilderImpl implements ToolResponse.Builder {
        private final List<ContentBlock> content = new ArrayList<>();
        private Object structuredContent;
        private boolean isError;
        private Map<String, Object> metadata;

        @Override
        public ToolResponse.Builder addContent(ContentBlock content) {
            this.content.add(content);
            return this;
        }

        @Override
        public ToolResponse.Builder addTextContent(String textContent) {
            this.content.add(new TextContentRecord(textContent, Optional.empty(), Map.of()));
            return this;
        }

        @Override
        public ToolResponse.Builder setStructuredContent(Object structuredContent) {
            this.structuredContent = structuredContent;
            return this;
        }

        @Override
        public ToolResponse.Builder setError(boolean isError) {
            this.isError = isError;
            return this;
        }

        @Override
        public ToolResponse.Builder putMetadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        @Override
        public ToolResponse.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public ToolResponse build() {
            return new ToolResponseRecord(List.copyOf(content),
                    Optional.ofNullable(structuredContent), isError,
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- PromptResponse ---

    @Override
    public PromptResponse.Builder promptResponseBuilder() {
        return new PromptResponseBuilderImpl();
    }

    record PromptMessageRecord(Role role, ContentBlock content) implements PromptMessage {
    }

    record PromptResponseRecord(Optional<String> description, List<PromptMessage> messages,
            Map<String, Object> metadata) implements PromptResponse {
    }

    static final class PromptResponseBuilderImpl implements PromptResponse.Builder {
        private String description;
        private final List<PromptMessage> messages = new ArrayList<>();
        private Map<String, Object> metadata;

        @Override
        public PromptResponse.Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public PromptResponse.Builder addMessage(Role role, ContentBlock content) {
            messages.add(new PromptMessageRecord(role, content));
            return this;
        }

        @Override
        public PromptResponse.Builder putMetadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        @Override
        public PromptResponse.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public PromptResponse build() {
            return new PromptResponseRecord(Optional.ofNullable(description),
                    List.copyOf(messages),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- TextResourceContents ---

    @Override
    public TextResourceContents.Builder textResourceContentsBuilder(String uri, String text) {
        return new TextResourceContentsBuilderImpl(uri, text);
    }

    record TextResourceContentsRecord(String uri, String text, Optional<String> mimeType,
            Map<String, Object> metadata) implements TextResourceContents {
    }

    // --- BlobResourceContents ---

    @Override
    public BlobResourceContents.Builder blobResourceContentsBuilder(String uri, byte[] data) {
        return new BlobResourceContentsBuilderImpl(uri, data);
    }

    record BlobResourceContentsRecord(String uri, byte[] blob, Optional<String> mimeType,
            Map<String, Object> metadata) implements BlobResourceContents {
    }

    // Shared base for resource contents builders (used by EmbeddedResource builder)
    static abstract class ResourceContentsBuilderBase<B extends ResourceContents.Builder<B>> {
        String mimeType;
        Map<String, Object> metadata;

        void setMimeTypeInternal(String mimeType) {
            this.mimeType = mimeType;
        }

        void putMetadataInternal(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
        }

        abstract ResourceContents buildInternal();
    }

    static final class TextResourceContentsBuilderImpl extends ResourceContentsBuilderBase<TextResourceContents.Builder>
            implements TextResourceContents.Builder {
        private final String uri;
        private final String text;

        TextResourceContentsBuilderImpl(String uri, String text) {
            this.uri = uri;
            this.text = text;
        }

        @Override
        public TextResourceContents.Builder setMimeType(String mimeType) {
            setMimeTypeInternal(mimeType);
            return this;
        }

        @Override
        public TextResourceContents.Builder putMetadata(String key, Object value) {
            putMetadataInternal(key, value);
            return this;
        }

        @Override
        public TextResourceContents.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public TextResourceContents build() {
            return (TextResourceContents) buildInternal();
        }

        @Override
        ResourceContents buildInternal() {
            return new TextResourceContentsRecord(uri, text, Optional.ofNullable(mimeType),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    static final class BlobResourceContentsBuilderImpl extends ResourceContentsBuilderBase<BlobResourceContents.Builder>
            implements BlobResourceContents.Builder {
        private final String uri;
        private final byte[] data;

        BlobResourceContentsBuilderImpl(String uri, byte[] data) {
            this.uri = uri;
            this.data = data;
        }

        @Override
        public BlobResourceContents.Builder setMimeType(String mimeType) {
            setMimeTypeInternal(mimeType);
            return this;
        }

        @Override
        public BlobResourceContents.Builder putMetadata(String key, Object value) {
            putMetadataInternal(key, value);
            return this;
        }

        @Override
        public BlobResourceContents.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public BlobResourceContents build() {
            return (BlobResourceContents) buildInternal();
        }

        @Override
        ResourceContents buildInternal() {
            return new BlobResourceContentsRecord(uri, data, Optional.ofNullable(mimeType),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- ResourceResponse ---

    @Override
    public ResourceResponse.Builder resourceResponseBuilder() {
        return new ResourceResponseBuilderImpl();
    }

    record ResourceResponseRecord(List<ResourceContents> contents,
            Map<String, Object> metadata) implements ResourceResponse {
        @Override
        public List<ResourceContents> getContents() {
            return contents;
        }
    }

    static final class ResourceResponseBuilderImpl implements ResourceResponse.Builder {
        private final List<ResourceContents> contents = new ArrayList<>();
        private Map<String, Object> metadata;

        @Override
        public ResourceResponse.Builder addContents(ResourceContents contents) {
            this.contents.add(contents);
            return this;
        }

        @Override
        public ResourceResponse.Builder putMetadata(String key, Object value) {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put(key, value);
            return this;
        }

        @Override
        public ResourceResponse.Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : null;
            return this;
        }

        @Override
        public ResourceResponse build() {
            return new ResourceResponseRecord(List.copyOf(contents),
                    metadata != null ? Map.copyOf(metadata) : Map.of());
        }
    }

    // --- Icon ---

    @Override
    public Icon.Builder iconBuilder(String uri) {
        return new IconBuilderImpl(uri);
    }

    record IconRecord(String src, Optional<String> mimeType, List<String> sizes,
            Optional<Icon.Theme> theme) implements Icon {
    }

    static final class IconBuilderImpl implements Icon.Builder {
        private final String src;
        private String mimeType;
        private final List<String> sizes = new ArrayList<>();
        private Icon.Theme theme;

        IconBuilderImpl(String src) {
            this.src = src;
        }

        @Override
        public Icon.Builder setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @Override
        public Icon.Builder addSize(int width, int height) {
            sizes.add(width + "x" + height);
            return this;
        }

        @Override
        public Icon.Builder setAnySize() {
            sizes.clear();
            sizes.add("any");
            return this;
        }

        @Override
        public Icon.Builder setTheme(Icon.Theme theme) {
            this.theme = theme;
            return this;
        }

        @Override
        public Icon build() {
            return new IconRecord(src, Optional.ofNullable(mimeType),
                    List.copyOf(sizes), Optional.ofNullable(theme));
        }
    }
}
