package io.quarkiverse.mcp.server.runtime.mcpjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mcpjava.server.content.Annotations;
import org.mcpjava.server.content.ContentBlock;

import io.quarkiverse.mcp.server.AudioContent;
import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceLink;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;

public class McpJavaTypeConverterTest {

    @Test
    public void testConvertTextContent() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Annotations ann = spi.annotationsBuilder()
                .setAudience(org.mcpjava.server.Role.USER)
                .setPriority(0.5)
                .build();

        org.mcpjava.server.content.TextContent tc = spi.textContentBuilder("hello world")
                .setAnnotations(ann)
                .putMetadata("key", "val")
                .build();

        Content result = McpJavaTypeConverter.convertContentBlock(tc);
        assertTrue(result instanceof TextContent);
        TextContent text = (TextContent) result;
        assertEquals("hello world", text.text());
        assertNotNull(text.annotations());
        assertEquals(0.5, text.annotations().priority());
        assertEquals(List.of(Role.USER), text.annotations().audience());
        assertNotNull(text._meta());
        assertEquals("val", text._meta().get(MetaKey.from("key")));
    }

    @Test
    public void testConvertImageContent() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        byte[] data = new byte[] { 1, 2, 3, 4, 5 };
        org.mcpjava.server.content.ImageContent ic = spi.imageContentBuilder(data, "image/png").build();

        Content result = McpJavaTypeConverter.convertContentBlock(ic);
        assertTrue(result instanceof ImageContent);
        ImageContent image = (ImageContent) result;
        assertEquals(Base64.getEncoder().encodeToString(data), image.data());
        assertEquals("image/png", image.mimeType());
        assertNull(image.annotations());
    }

    @Test
    public void testConvertAudioContent() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        byte[] data = new byte[] { 10, 20, 30 };
        org.mcpjava.server.content.AudioContent ac = spi.audioContentBuilder(data, "audio/wav").build();

        Content result = McpJavaTypeConverter.convertContentBlock(ac);
        assertTrue(result instanceof AudioContent);
        AudioContent audio = (AudioContent) result;
        assertEquals(Base64.getEncoder().encodeToString(data), audio.data());
        assertEquals("audio/wav", audio.mimeType());
    }

    @Test
    public void testConvertEmbeddedResourceWithTextContents() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        org.mcpjava.server.content.EmbeddedResource er = spi
                .textEmbeddedResourceBuilder("content", "file:///test.txt")
                .setMimeType("text/plain")
                .build();

        Content result = McpJavaTypeConverter.convertContentBlock(er);
        assertTrue(result instanceof EmbeddedResource);
        EmbeddedResource embedded = (EmbeddedResource) result;
        assertTrue(embedded.resource() instanceof TextResourceContents);
        TextResourceContents trc = (TextResourceContents) embedded.resource();
        assertEquals("file:///test.txt", trc.uri());
        assertEquals("content", trc.text());
        assertEquals("text/plain", trc.mimeType());
    }

    @Test
    public void testConvertEmbeddedResourceWithBlobContents() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        byte[] data = new byte[] { 7, 8, 9 };
        org.mcpjava.server.content.EmbeddedResource er = spi
                .blobEmbeddedResourceBuilder(data, "file:///test.bin")
                .build();

        Content result = McpJavaTypeConverter.convertContentBlock(er);
        assertTrue(result instanceof EmbeddedResource);
        EmbeddedResource embedded = (EmbeddedResource) result;
        assertTrue(embedded.resource() instanceof BlobResourceContents);
        BlobResourceContents brc = (BlobResourceContents) embedded.resource();
        assertEquals("file:///test.bin", brc.uri());
        assertEquals(Base64.getEncoder().encodeToString(data), brc.blob());
    }

    @Test
    public void testConvertResourceLink() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        org.mcpjava.server.content.ResourceLink rl = spi.resourceLinkBuilder("readme", "file:///readme.md")
                .setTitle("README")
                .setDescription("Project readme")
                .setMimeType("text/markdown")
                .setSize(2048)
                .putMetadata("source", "git")
                .build();

        Content result = McpJavaTypeConverter.convertContentBlock(rl);
        assertTrue(result instanceof ResourceLink);
        ResourceLink link = (ResourceLink) result;
        assertEquals("file:///readme.md", link.uri());
        assertEquals("text/markdown", link.mimeType());
        assertEquals("readme", link.name());
        assertEquals("README", link.title());
        assertEquals("Project readme", link.description());
        assertEquals(2048, link.size());
        assertNotNull(link._meta());
        assertEquals("git", link._meta().get(MetaKey.from("source")));
    }

    @Test
    public void testConvertResourceLinkNoOptionals() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        org.mcpjava.server.content.ResourceLink rl = spi.resourceLinkBuilder("file", "file:///f").build();

        Content result = McpJavaTypeConverter.convertContentBlock(rl);
        ResourceLink link = (ResourceLink) result;
        assertEquals("file:///f", link.uri());
        assertNull(link.mimeType());
        assertEquals("file", link.name());
        assertNull(link.description());
        assertNull(link.size());
    }

    @Test
    public void testConvertTextResourceContents() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        org.mcpjava.server.resources.TextResourceContents trc = spi
                .textResourceContentsBuilder("file:///test", "data")
                .setMimeType("text/plain")
                .putMetadata("encoding", "utf-8")
                .build();

        ResourceContents result = McpJavaTypeConverter.convertResourceContents(trc);
        assertTrue(result instanceof TextResourceContents);
        TextResourceContents text = (TextResourceContents) result;
        assertEquals("file:///test", text.uri());
        assertEquals("data", text.text());
        assertEquals("text/plain", text.mimeType());
        assertEquals("utf-8", text._meta().get(MetaKey.from("encoding")));
    }

    @Test
    public void testConvertBlobResourceContents() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        byte[] data = new byte[] { 42, 43, 44 };
        org.mcpjava.server.resources.BlobResourceContents brc = spi
                .blobResourceContentsBuilder("file:///bin", data)
                .setMimeType("application/octet-stream")
                .build();

        ResourceContents result = McpJavaTypeConverter.convertResourceContents(brc);
        assertTrue(result instanceof BlobResourceContents);
        BlobResourceContents blob = (BlobResourceContents) result;
        assertEquals("file:///bin", blob.uri());
        assertEquals(Base64.getEncoder().encodeToString(data), blob.blob());
        assertEquals("application/octet-stream", blob.mimeType());
    }

    @Test
    public void testConvertPromptMessage() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        org.mcpjava.server.content.TextContent tc = spi.textContentBuilder("hello").build();
        QuarkusMcpServerSPI.PromptMessageRecord pm = new QuarkusMcpServerSPI.PromptMessageRecord(
                org.mcpjava.server.Role.ASSISTANT, tc);

        PromptMessage result = McpJavaTypeConverter.convertPromptMessage(pm);
        assertEquals(Role.ASSISTANT, result.role());
        assertTrue(result.content() instanceof TextContent);
        assertEquals("hello", result.content().asText().text());
    }

    @Test
    public void testConvertAnnotationsWithAllFields() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Instant now = Instant.parse("2025-06-15T10:00:00Z");
        Annotations ann = spi.annotationsBuilder()
                .setAudience(org.mcpjava.server.Role.USER, org.mcpjava.server.Role.ASSISTANT)
                .setPriority(0.8)
                .setLastModified(now)
                .build();

        Content.Annotations result = McpJavaTypeConverter.convertAnnotations(ann);
        assertNotNull(result);
        assertEquals(2, result.audience().size());
        assertTrue(result.audience().contains(Role.USER));
        assertTrue(result.audience().contains(Role.ASSISTANT));
        assertEquals(0.8, result.priority());
        assertEquals("2025-06-15T10:00:00Z", result.lastModified());
    }

    @Test
    public void testConvertAnnotationsNull() {
        assertNull(McpJavaTypeConverter.convertAnnotations(null));
    }

    @Test
    public void testConvertAnnotationsEmpty() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Annotations ann = spi.annotationsBuilder().build();

        Content.Annotations result = McpJavaTypeConverter.convertAnnotations(ann);
        assertNotNull(result);
        assertNull(result.audience());
        assertNull(result.lastModified());
        assertNull(result.priority());
    }

    @Test
    public void testConvertRoleUser() {
        assertEquals(Role.USER, McpJavaTypeConverter.convertRole(org.mcpjava.server.Role.USER));
    }

    @Test
    public void testConvertRoleAssistant() {
        assertEquals(Role.ASSISTANT, McpJavaTypeConverter.convertRole(org.mcpjava.server.Role.ASSISTANT));
    }

    @Test
    public void testConvertMetadataNull() {
        assertNull(McpJavaTypeConverter.convertMetadata(null));
    }

    @Test
    public void testConvertMetadataEmpty() {
        assertNull(McpJavaTypeConverter.convertMetadata(Map.of()));
    }

    @Test
    public void testConvertMetadataWithEntries() {
        Map<MetaKey, Object> result = McpJavaTypeConverter.convertMetadata(Map.of("foo", "bar", "num", 42));
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("bar", result.get(MetaKey.from("foo")));
        assertEquals(42, result.get(MetaKey.from("num")));
    }

    @Test
    public void testConvertContentBlocksList() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        List<ContentBlock> blocks = List.of(
                spi.textContentBuilder("one").build(),
                spi.textContentBuilder("two").build());

        List<Content> result = McpJavaTypeConverter.convertContentBlocks(blocks);
        assertEquals(2, result.size());
        assertEquals("one", result.get(0).asText().text());
        assertEquals("two", result.get(1).asText().text());
    }

    @Test
    public void testConvertResourceContentsList() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        List<org.mcpjava.server.resources.ResourceContents> contents = List.of(
                spi.textResourceContentsBuilder("file:///a", "aaa").build(),
                spi.textResourceContentsBuilder("file:///b", "bbb").build());

        List<ResourceContents> result = McpJavaTypeConverter.convertResourceContentsList(contents);
        assertEquals(2, result.size());
        assertEquals("aaa", result.get(0).asText().text());
        assertEquals("bbb", result.get(1).asText().text());
    }

}
