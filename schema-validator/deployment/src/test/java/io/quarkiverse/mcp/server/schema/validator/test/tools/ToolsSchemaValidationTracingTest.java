package io.quarkiverse.mcp.server.schema.validator.test.tools;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.QuarkusUnitTest;

public class ToolsSchemaValidationTracingTest extends McpServerTest {

    private static final AttributeKey<String> MCP_METHOD_NAME = AttributeKey.stringKey("mcp.method.name");

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .overrideConfigKey("quarkus.otel.traces.exporter", "cdi")
            .overrideConfigKey("quarkus.otel.bsp.schedule.delay", "100ms")
            .overrideConfigKey("quarkus.otel.metrics.exporter", "none")
            .overrideConfigKey("quarkus.otel.logs.exporter", "none")
            .overrideConfigKey("quarkus.mcp.server.tracing.enabled", "true")
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, InMemorySpanExporterProducer.class));

    @BeforeEach
    void resetSpans() {
        InMemorySpanExporterProducer.EXPORTER.reset();
    }

    @Test
    public void testValidCallProducesSuccessSpan() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsCall("bravo", Map.of("price", 42), toolResponse -> {
                    assertEquals("foo42", toolResponse.content().get(0).asText().text());
                })
                .thenAssertResults();

        SpanData span = awaitSpan("tools/call bravo");
        assertEquals(SpanKind.SERVER, span.getKind());
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
    }

    @Test
    public void testValidationRejectedRequestProducesErrorSpan() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        // Send a tools/call request without the required "params" field -> schema validation fails
        client.when()
                .message(client.newRequest("tools/call"))
                .withErrorAssert(error -> {
                    assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                    assertTrue(error.message().startsWith("Schema validation failed"));
                })
                .send()
                .thenAssertResults();

        SpanData errorSpan = awaitSpan("tools/call", StatusCode.ERROR);
        assertEquals(SpanKind.SERVER, errorSpan.getKind());
        assertEquals("tools/call", errorSpan.getAttributes().get(MCP_METHOD_NAME));
        assertEquals(StatusCode.ERROR, errorSpan.getStatus().getStatusCode());
        assertNotNull(errorSpan.getAttributes().get(RpcIncubatingAttributes.RPC_JSONRPC_REQUEST_ID));
        assertEquals(String.valueOf(JsonRpcErrorCodes.INVALID_REQUEST),
                errorSpan.getAttributes().get(ErrorAttributes.ERROR_TYPE));
        assertEquals(Long.valueOf(JsonRpcErrorCodes.INVALID_REQUEST),
                errorSpan.getAttributes().get(RpcIncubatingAttributes.RPC_JSONRPC_ERROR_CODE));
        assertNotNull(errorSpan.getAttributes().get(RpcIncubatingAttributes.RPC_JSONRPC_ERROR_MESSAGE));
    }

    private SpanData awaitSpan(String spanName) {
        return awaitSpan(spanName, null);
    }

    private SpanData awaitSpan(String spanName, StatusCode statusCode) {
        AtomicReference<List<SpanData>> holder = new AtomicReference<>();
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    holder.set(InMemorySpanExporterProducer.EXPORTER.getFinishedSpanItems().stream()
                            .filter(s -> s.getName().equals(spanName))
                            .filter(s -> statusCode == null || s.getStatus().getStatusCode() == statusCode)
                            .toList());
                    return !holder.get().isEmpty();
                });
        List<SpanData> spans = holder.get();
        return spans.get(spans.size() - 1);
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "foo" + price;
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
