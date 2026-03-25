package io.quarkiverse.mcp.server.test.tracing;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class TracingTest extends McpServerTest {

    private static final AttributeKey<String> MCP_METHOD_NAME = AttributeKey.stringKey("mcp.method.name");
    private static final AttributeKey<String> MCP_SESSION_ID = AttributeKey.stringKey("mcp.session.id");
    private static final AttributeKey<String> MCP_PROTOCOL_VERSION = AttributeKey.stringKey("mcp.protocol.version");
    private static final AttributeKey<String> GEN_AI_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    private static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> GEN_AI_PROMPT_NAME = AttributeKey.stringKey("gen_ai.prompt.name");
    private static final AttributeKey<String> MCP_RESOURCE_URI = AttributeKey.stringKey("mcp.resource.uri");
    private static final AttributeKey<String> NETWORK_TRANSPORT = AttributeKey.stringKey("network.transport");

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideConfigKey("quarkus.otel.bsp.schedule.delay", "100ms")
            .overrideConfigKey("quarkus.otel.metrics.exporter", "none")
            .overrideConfigKey("quarkus.otel.logs.exporter", "none")
            .overrideConfigKey("quarkus.mcp.server.tracing.enabled", "true")
            .withApplicationRoot(
                    root -> root.addClasses(MyFeatures.class, InMemorySpanExporterProducer.class));

    @BeforeEach
    void resetSpans() {
        InMemorySpanExporterProducer.EXPORTER.reset();
    }

    @Test
    void testToolsListSpan() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(p -> assertNotNull(p))
                .thenAssertResults();

        SpanData span = awaitSpan("tools/list");
        assertEquals(SpanKind.SERVER, span.getKind());
        assertEquals("tools/list", span.getAttributes().get(MCP_METHOD_NAME));
        assertNotNull(span.getAttributes().get(MCP_SESSION_ID));
        assertNotNull(span.getAttributes().get(MCP_PROTOCOL_VERSION));
        assertEquals("tcp", span.getAttributes().get(NETWORK_TRANSPORT));
    }

    @Test
    void testToolsCallSpan() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("alpha", Map.of("price", 5), r -> {
                    assertEquals("10", r.firstContent().asText().text());
                })
                .thenAssertResults();

        SpanData span = awaitSpan("tools/call alpha");
        assertEquals(SpanKind.SERVER, span.getKind());
        assertEquals("tools/call", span.getAttributes().get(MCP_METHOD_NAME));
        assertEquals("alpha", span.getAttributes().get(GEN_AI_TOOL_NAME));
        assertEquals("execute_tool", span.getAttributes().get(GEN_AI_OPERATION_NAME));
        assertNotNull(span.getAttributes().get(MCP_PROTOCOL_VERSION));
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
    }

    @Test
    void testPromptsGetSpan() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .promptsGet("charlie", r -> assertNotNull(r))
                .thenAssertResults();

        SpanData span = awaitSpan("prompts/get charlie");
        assertEquals(SpanKind.SERVER, span.getKind());
        assertEquals("prompts/get", span.getAttributes().get(MCP_METHOD_NAME));
        assertEquals("charlie", span.getAttributes().get(GEN_AI_PROMPT_NAME));
        assertNotNull(span.getAttributes().get(MCP_PROTOCOL_VERSION));
    }

    @Test
    void testResourcesReadSpan() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .resourcesRead("file:///project/delta", r -> assertNotNull(r))
                .thenAssertResults();

        SpanData span = awaitSpan("resources/read");
        assertEquals(SpanKind.SERVER, span.getKind());
        assertEquals("resources/read", span.getAttributes().get(MCP_METHOD_NAME));
        assertEquals("file:///project/delta", span.getAttributes().get(MCP_RESOURCE_URI));
        assertNotNull(span.getAttributes().get(MCP_PROTOCOL_VERSION));
    }

    @Test
    void testInitializeSpan() {
        McpAssured.newConnectedStreamableClient();

        SpanData span = awaitSpan("initialize");
        assertEquals(SpanKind.SERVER, span.getKind());
        assertEquals("initialize", span.getAttributes().get(MCP_METHOD_NAME));
    }

    private SpanData awaitSpan(String spanName) {
        AtomicReference<List<SpanData>> holder = new AtomicReference<>();
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    holder.set(InMemorySpanExporterProducer.EXPORTER.getFinishedSpanItems().stream()
                            .filter(s -> s.getName().equals(spanName))
                            .toList());
                    return !holder.get().isEmpty();
                });
        List<SpanData> spans = holder.get();
        return spans.get(spans.size() - 1);
    }

    public static class MyFeatures {

        @Tool
        String alpha(@ToolArg(defaultValue = "10") int price) {
            return "" + price * 2;
        }

        @Tool
        String bravo(@ToolArg(defaultValue = "10") int price) {
            return "" + price * 2;
        }

        @Prompt
        PromptResponse charlie() {
            return PromptResponse.withMessages(PromptMessage.withUserRole("ok"));
        }

        @Resource(uri = "file:///project/delta")
        TextResourceContents delta(RequestUri uri) {
            return TextResourceContents.create(uri.value(), "3");
        }

        @ResourceTemplate(uriTemplate = "file:///{path}")
        TextResourceContents echo(String path, RequestUri uri) {
            return TextResourceContents.create(uri.value(), "foo:" + path);
        }
    }

    @ApplicationScoped
    public static class InMemorySpanExporterProducer {

        static final InMemorySpanExporter EXPORTER = InMemorySpanExporter.create();

        @Produces
        @Singleton
        InMemorySpanExporter exporter() {
            return EXPORTER;
        }
    }
}
