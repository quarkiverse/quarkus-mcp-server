package io.quarkiverse.mcp.server.runtime.mcpjava;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mcpjava.server.Icon;
import org.mcpjava.server.Role;
import org.mcpjava.server.completion.CompletionResult;
import org.mcpjava.server.content.Annotations;
import org.mcpjava.server.content.AudioContent;
import org.mcpjava.server.content.EmbeddedResource;
import org.mcpjava.server.content.ImageContent;
import org.mcpjava.server.content.ResourceLink;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.PromptResponse;
import org.mcpjava.server.resources.BlobResourceContents;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.resources.TextResourceContents;
import org.mcpjava.server.tools.ToolResponse;

public class QuarkusMcpServerSPITest {

    @Test
    public void testImageContentBuilder() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        byte[] data = new byte[] { 1, 2, 3 };
        Annotations annotations = spi.annotationsBuilder().setPriority(0.5).build();

        ImageContent ic = spi.imageContentBuilder(data, "image/png")
                .setAnnotations(annotations)
                .putMetadata("key1", "val1")
                .build();

        assertArrayEquals(data, ic.data());
        assertEquals("image/png", ic.mimeType());
        assertTrue(ic.annotations().isPresent());
        assertEquals(0.5, ic.annotations().get().priority().getAsDouble());
        assertEquals("val1", ic.metadata().get("key1"));
    }

    @Test
    public void testAudioContentBuilder() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        byte[] data = new byte[] { 10, 20, 30 };
        AudioContent ac = spi.audioContentBuilder(data, "audio/wav")
                .putMetadata("track", "1")
                .build();

        assertArrayEquals(data, ac.data());
        assertEquals("audio/wav", ac.mimeType());
        assertEquals("1", ac.metadata().get("track"));
        assertFalse(ac.annotations().isPresent());
    }

    @Test
    public void testAnnotationsBuilder() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Instant now = Instant.parse("2025-06-15T10:30:00Z");

        Annotations ann = spi.annotationsBuilder()
                .setAudience(Role.USER, Role.ASSISTANT)
                .setPriority(0.9)
                .setLastModified(now)
                .build();

        assertTrue(ann.audience().isPresent());
        assertEquals(Set.of(Role.USER, Role.ASSISTANT), ann.audience().get());
        assertEquals(OptionalDouble.of(0.9), ann.priority());
        assertTrue(ann.lastModified().isPresent());
        assertEquals(now, ann.lastModified().get());
    }

    @Test
    public void testAnnotationsBuilderWithSetAudience() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Annotations ann = spi.annotationsBuilder()
                .setAudience(Set.of(Role.USER))
                .build();

        assertTrue(ann.audience().isPresent());
        assertEquals(Set.of(Role.USER), ann.audience().get());
        assertEquals(OptionalDouble.empty(), ann.priority());
        assertFalse(ann.lastModified().isPresent());
    }

    @Test
    public void testAnnotationsBuilderEmpty() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Annotations ann = spi.annotationsBuilder().build();

        assertFalse(ann.audience().isPresent());
        assertEquals(OptionalDouble.empty(), ann.priority());
        assertFalse(ann.lastModified().isPresent());
    }

    @Test
    public void testTextEmbeddedResourceBuilder() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Annotations annotations = spi.annotationsBuilder().setPriority(0.7).build();

        EmbeddedResource er = spi.textEmbeddedResourceBuilder("hello", "file:///test.txt")
                .setAnnotations(annotations)
                .setMimeType("text/plain")
                .putResourceMeta("lang", "en")
                .putMetadata("key", "value")
                .build();

        assertTrue(er.resource() instanceof TextResourceContents);
        TextResourceContents trc = (TextResourceContents) er.resource();
        assertEquals("file:///test.txt", trc.uri());
        assertEquals("hello", trc.text());
        assertTrue(trc.mimeType().isPresent());
        assertEquals("text/plain", trc.mimeType().get());
        assertEquals("en", trc.metadata().get("lang"));
        assertTrue(er.annotations().isPresent());
        assertEquals("value", er.metadata().get("key"));
    }

    @Test
    public void testBlobEmbeddedResourceBuilder() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        byte[] data = new byte[] { 5, 6, 7 };
        EmbeddedResource er = spi.blobEmbeddedResourceBuilder(data, "file:///test.bin")
                .setMimeType("application/octet-stream")
                .build();

        assertTrue(er.resource() instanceof BlobResourceContents);
        BlobResourceContents brc = (BlobResourceContents) er.resource();
        assertEquals("file:///test.bin", brc.uri());
        assertArrayEquals(data, brc.blob());
        assertTrue(brc.mimeType().isPresent());
        assertEquals("application/octet-stream", brc.mimeType().get());
        assertFalse(er.annotations().isPresent());
        assertTrue(er.metadata().isEmpty());
    }

    @Test
    public void testResourceLinkBuilder() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Annotations annotations = spi.annotationsBuilder().setAudience(Role.USER).build();

        ResourceLink rl = spi.resourceLinkBuilder("readme", "file:///readme.md")
                .setTitle("README")
                .setDescription("Project readme")
                .setMimeType("text/markdown")
                .setAnnotations(annotations)
                .setSize(1024)
                .putMetadata("source", "git")
                .build();

        assertEquals("readme", rl.name());
        assertEquals("README", rl.title());
        assertEquals("file:///readme.md", rl.uri());
        assertTrue(rl.description().isPresent());
        assertEquals("Project readme", rl.description().get());
        assertTrue(rl.mimeType().isPresent());
        assertEquals("text/markdown", rl.mimeType().get());
        assertTrue(rl.annotations().isPresent());
        assertEquals(OptionalLong.of(1024), rl.size());
        assertEquals("git", rl.metadata().get("source"));
    }

    @Test
    public void testResourceLinkBuilderDefaults() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        ResourceLink rl = spi.resourceLinkBuilder("myfile", "file:///myfile")
                .build();

        assertEquals("myfile", rl.name());
        assertEquals("myfile", rl.title());
        assertEquals("file:///myfile", rl.uri());
        assertFalse(rl.description().isPresent());
        assertFalse(rl.mimeType().isPresent());
        assertFalse(rl.annotations().isPresent());
        assertEquals(OptionalLong.empty(), rl.size());
        assertTrue(rl.metadata().isEmpty());
    }

    @Test
    public void testIconBuilder() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Icon icon = spi.iconBuilder("https://example.com/icon.png")
                .setMimeType("image/png")
                .addSize(16, 16)
                .addSize(32, 32)
                .setTheme(Icon.Theme.DARK)
                .build();

        assertEquals("https://example.com/icon.png", icon.src());
        assertTrue(icon.mimeType().isPresent());
        assertEquals("image/png", icon.mimeType().get());
        assertEquals(List.of("16x16", "32x32"), icon.sizes());
        assertTrue(icon.theme().isPresent());
        assertEquals(Icon.Theme.DARK, icon.theme().get());
    }

    @Test
    public void testIconBuilderAnySize() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Icon icon = spi.iconBuilder("https://example.com/icon.svg")
                .addSize(16, 16)
                .setAnySize()
                .build();

        assertEquals(List.of("any"), icon.sizes());
        assertFalse(icon.mimeType().isPresent());
        assertFalse(icon.theme().isPresent());
    }

    @Test
    public void testBlobResourceContentsBuilder() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        byte[] data = new byte[] { 0, 1, 2, 3 };
        BlobResourceContents brc = spi.blobResourceContentsBuilder("file:///data.bin", data)
                .setMimeType("application/octet-stream")
                .putMetadata("size", 4)
                .build();

        assertEquals("file:///data.bin", brc.uri());
        assertArrayEquals(data, brc.blob());
        assertTrue(brc.mimeType().isPresent());
        assertEquals("application/octet-stream", brc.mimeType().get());
        assertEquals(4, brc.metadata().get("size"));
    }

    @Test
    public void testTextContentBuilderWithMetadataAndAnnotations() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        Annotations ann = spi.annotationsBuilder().setPriority(0.3).build();
        TextContent tc = spi.textContentBuilder("hello")
                .setAnnotations(ann)
                .putMetadata("k1", "v1")
                .putMetadata("k2", "v2")
                .build();

        assertEquals("hello", tc.text());
        assertTrue(tc.annotations().isPresent());
        assertEquals(2, tc.metadata().size());
    }

    @Test
    public void testTextContentBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        TextContent tc = spi.textContentBuilder("text")
                .setMetadata(Map.of("a", 1, "b", 2))
                .build();

        assertEquals(2, tc.metadata().size());

        tc = spi.textContentBuilder("text")
                .setMetadata(Map.of("a", 1))
                .setMetadata(null)
                .build();
        assertTrue(tc.metadata().isEmpty());
    }

    @Test
    public void testToolResponseBuilderExtended() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        TextContent tc = spi.textContentBuilder("error msg").build();
        ToolResponse tr = spi.toolResponseBuilder()
                .addContent(tc)
                .addTextContent("extra text")
                .setStructuredContent(Map.of("code", 500))
                .setError(true)
                .putMetadata("retry", false)
                .build();

        assertTrue(tr.isError());
        assertEquals(2, tr.content().size());
        assertTrue(tr.structuredContent().isPresent());
        assertEquals(Map.of("code", 500), tr.structuredContent().get());
        assertEquals(false, tr.metadata().get("retry"));
    }

    @Test
    public void testCompletionResultBuilderMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        CompletionResult cr = spi.completeResultBuilder()
                .addValue("alpha")
                .addValues(List.of("bravo", "charlie"))
                .setTotal(10)
                .setHasMore(true)
                .putMetadata("source", "db")
                .build();

        assertEquals(List.of("alpha", "bravo", "charlie"), cr.values());
        assertEquals(OptionalInt.of(10), cr.total());
        assertTrue(cr.hasMore().isPresent());
        assertTrue(cr.hasMore().get());
        assertEquals("db", cr.metadata().get("source"));
    }

    @Test
    public void testCompletionResultBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        CompletionResult cr = spi.completeResultBuilder()
                .setMetadata(Map.of("x", 1))
                .build();

        assertEquals(1, cr.metadata().get("x"));

        cr = spi.completeResultBuilder()
                .setMetadata(null)
                .build();
        assertTrue(cr.metadata().isEmpty());
    }

    @Test
    public void testPromptResponseBuilderMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        TextContent tc = spi.textContentBuilder("hello").build();
        PromptResponse pr = spi.promptResponseBuilder()
                .setDescription("A test prompt")
                .addMessage(Role.USER, tc)
                .putMetadata("version", "1.0")
                .build();

        assertTrue(pr.description().isPresent());
        assertEquals("A test prompt", pr.description().get());
        assertEquals(1, pr.messages().size());
        assertEquals(Role.USER, pr.messages().get(0).role());
        assertEquals("1.0", pr.metadata().get("version"));
    }

    @Test
    public void testPromptResponseBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        PromptResponse pr = spi.promptResponseBuilder()
                .setMetadata(Map.of("a", 1))
                .setMetadata(null)
                .build();
        assertTrue(pr.metadata().isEmpty());
    }

    @Test
    public void testResourceResponseBuilderMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        TextResourceContents trc = spi.textResourceContentsBuilder("file:///a", "content").build();
        ResourceResponse rr = spi.resourceResponseBuilder()
                .addContents(trc)
                .putMetadata("cached", true)
                .build();

        assertEquals(1, rr.getContents().size());
        assertEquals(true, rr.metadata().get("cached"));
    }

    @Test
    public void testResourceResponseBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        ResourceResponse rr = spi.resourceResponseBuilder()
                .setMetadata(Map.of("k", "v"))
                .setMetadata(null)
                .build();
        assertTrue(rr.metadata().isEmpty());
    }

    @Test
    public void testTextResourceContentsBuilderMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        TextResourceContents trc = spi.textResourceContentsBuilder("file:///test", "data")
                .setMimeType("text/plain")
                .putMetadata("encoding", "utf-8")
                .build();

        assertEquals("file:///test", trc.uri());
        assertEquals("data", trc.text());
        assertTrue(trc.mimeType().isPresent());
        assertEquals("text/plain", trc.mimeType().get());
        assertEquals("utf-8", trc.metadata().get("encoding"));
    }

    @Test
    public void testTextResourceContentsBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        TextResourceContents trc = spi.textResourceContentsBuilder("file:///t", "text")
                .setMetadata(Map.of("a", 1))
                .setMetadata(null)
                .build();
        assertTrue(trc.metadata().isEmpty());
    }

    @Test
    public void testBlobResourceContentsBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        BlobResourceContents brc = spi.blobResourceContentsBuilder("file:///b", new byte[] { 1 })
                .setMetadata(Map.of("a", 1))
                .build();
        assertEquals(1, brc.metadata().get("a"));

        brc = spi.blobResourceContentsBuilder("file:///b", new byte[] { 1 })
                .setMetadata(null)
                .build();
        assertTrue(brc.metadata().isEmpty());
    }

    @Test
    public void testImageContentBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        ImageContent ic = spi.imageContentBuilder(new byte[] { 1 }, "image/png")
                .setMetadata(Map.of("w", 100))
                .build();
        assertEquals(100, ic.metadata().get("w"));

        ic = spi.imageContentBuilder(new byte[] { 1 }, "image/png")
                .setMetadata(null)
                .build();
        assertTrue(ic.metadata().isEmpty());
    }

    @Test
    public void testAudioContentBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        AudioContent ac = spi.audioContentBuilder(new byte[] { 1 }, "audio/mp3")
                .setMetadata(Map.of("ch", 2))
                .build();
        assertEquals(2, ac.metadata().get("ch"));

        ac = spi.audioContentBuilder(new byte[] { 1 }, "audio/mp3")
                .setMetadata(null)
                .build();
        assertTrue(ac.metadata().isEmpty());
    }

    @Test
    public void testEmbeddedResourceBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        EmbeddedResource er = spi.textEmbeddedResourceBuilder("text", "file:///t")
                .setMetadata(Map.of("x", "y"))
                .build();
        assertEquals("y", er.metadata().get("x"));

        er = spi.textEmbeddedResourceBuilder("text", "file:///t")
                .setMetadata(null)
                .build();
        assertTrue(er.metadata().isEmpty());
    }

    @Test
    public void testResourceLinkBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        ResourceLink rl = spi.resourceLinkBuilder("n", "u")
                .setMetadata(Map.of("k", "v"))
                .build();
        assertEquals("v", rl.metadata().get("k"));

        rl = spi.resourceLinkBuilder("n", "u")
                .setMetadata(null)
                .build();
        assertTrue(rl.metadata().isEmpty());
    }

    @Test
    public void testToolResponseBuilderSetMetadata() {
        QuarkusMcpServerSPI spi = new QuarkusMcpServerSPI();
        ToolResponse tr = spi.toolResponseBuilder()
                .setMetadata(Map.of("k", "v"))
                .build();
        assertEquals("v", tr.metadata().get("k"));

        tr = spi.toolResponseBuilder()
                .setMetadata(null)
                .build();
        assertTrue(tr.metadata().isEmpty());
    }
}
