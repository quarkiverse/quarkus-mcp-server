package io.quarkiverse.mcp.server.runtime.tracing;

import java.util.Collections;

import jakarta.inject.Singleton;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.runtime.McpRequest;
import io.quarkiverse.mcp.server.runtime.McpTracing;
import io.quarkiverse.mcp.server.runtime.McpTracingSpan;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.opentelemetry.runtime.config.runtime.OTelRuntimeConfig;
import io.vertx.core.json.JsonObject;

@Singleton
public class McpTracingInstrumenter implements McpTracing {

    private static final String INSTRUMENTATION_NAME = "io.quarkus.opentelemetry.mcp";

    private static final AttributeKey<String> MCP_METHOD_NAME = AttributeKey.stringKey("mcp.method.name");
    private static final AttributeKey<String> MCP_SESSION_ID = AttributeKey.stringKey("mcp.session.id");
    private static final AttributeKey<String> MCP_PROTOCOL_VERSION = AttributeKey.stringKey("mcp.protocol.version");
    private static final AttributeKey<String> MCP_RESOURCE_URI = AttributeKey.stringKey("mcp.resource.uri");
    private static final AttributeKey<String> GEN_AI_PROMPT_NAME = AttributeKey.stringKey("gen_ai.prompt.name");

    private final Instrumenter<McpRequestInfo, McpResponseInfo> instrumenter;
    private final OpenTelemetry openTelemetry;

    public McpTracingInstrumenter(OpenTelemetry openTelemetry, OTelRuntimeConfig otelConfig,
            McpServersRuntimeConfig mcpConfig) {
        this.openTelemetry = openTelemetry;

        InstrumenterBuilder<McpRequestInfo, McpResponseInfo> builder = Instrumenter.builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                new McpSpanNameExtractor());

        builder.addAttributesExtractor(new McpAttributesExtractor())
                .setSpanStatusExtractor(new McpSpanStatusExtractor())
                .addSpanLinksExtractor(new McpAmbientContextLinksExtractor())
                .setEnabled(!otelConfig.sdkDisabled()
                        && mcpConfig.servers().get(McpServer.DEFAULT).tracingEnabled());

        // Use buildInstrumenter with SERVER kind instead of buildServerInstrumenter
        // to avoid nested server span suppression (Quarkus HTTP already creates a server span)
        this.instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
    }

    /**
     * Starts a tracing span for the given MCP request.
     * Returns a {@link TracingSpan} handle if tracing starts, or {@code null} if it should not be traced.
     * This method encapsulates all OTel types so callers don't need OTel on the classpath.
     */
    @Override
    public McpTracingSpan startSpan(McpMethod method, JsonObject message, McpRequest mcpRequest,
            InitialRequest.Transport transport) {
        McpRequestInfo requestInfo = new McpRequestInfo(method, message, mcpRequest, transport);

        // Capture the ambient transport context (e.g., HTTP server span) before switching to root
        Context ambientContext = Context.current();
        requestInfo.setAmbientContext(ambientContext);

        // Use Context.root() to avoid inheriting the HTTP server span's sampling decision.
        // MCP spans should start their own trace, optionally parented via _meta propagation.
        Context parentContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.root(), requestInfo, new McpMetaTextMapGetter());
        Instrumenter<McpRequestInfo, McpResponseInfo> inst = instrumenter;
        if (inst.shouldStart(parentContext, requestInfo)) {
            Context otelContext = inst.start(parentContext, requestInfo);
            Scope otelScope = otelContext.makeCurrent();

            return new TracingSpan(requestInfo, otelContext, otelScope);
        }
        return McpTracingSpan.NOOP;
    }

    class TracingSpan implements McpTracingSpan {
        private final McpRequestInfo requestInfo;
        private final Context otelContext;
        private final Scope otelScope;

        TracingSpan(McpRequestInfo requestInfo, Context otelContext, Scope otelScope) {
            this.requestInfo = requestInfo;
            this.otelContext = otelContext;
            this.otelScope = otelScope;
        }

        @Override
        public McpRequestInfo requestInfo() {
            return requestInfo;
        }

        @Override
        public void end(Throwable error) {
            try (otelScope) {
                instrumenter.end(otelContext, requestInfo, requestInfo.getResponseInfo(), error);
            }
        }

    }

    @Override
    public void injectMcpOtelContext(JsonObject meta) {
        openTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), meta, JsonObjectTextMapSetter.INSTANCE);
    }

    // ---- Inner classes ----

    private enum JsonObjectTextMapSetter implements TextMapSetter<JsonObject> {
        INSTANCE;

        @Override
        public void set(JsonObject carrier, String key, String value) {
            if (carrier != null) {
                carrier.put(key, value);
            }
        }
    }

    private static class McpAmbientContextLinksExtractor implements SpanLinksExtractor<McpRequestInfo> {
        @Override
        public void extract(SpanLinksBuilder spanLinks, Context parentContext, McpRequestInfo request) {
            Context ambientContext = request.getAmbientContext();
            if (ambientContext != null) {
                SpanContext spanContext = Span.fromContext(ambientContext).getSpanContext();
                if (spanContext != null && spanContext.isSampled() && spanContext.isValid()) {
                    spanLinks.addLink(spanContext);
                }
            }
        }
    }

    private static class McpSpanNameExtractor implements SpanNameExtractor<McpRequestInfo> {
        @Override
        public String extract(McpRequestInfo request) {
            String method = request.method().jsonRpcName();
            return switch (request.method()) {
                case TOOLS_CALL -> {
                    String name = request.getToolName();
                    yield name != null ? method + " " + name : method;
                }
                case PROMPTS_GET -> {
                    String name = request.getPromptName();
                    yield name != null ? method + " " + name : method;
                }
                default -> method;
            };
        }
    }

    private static class McpAttributesExtractor implements AttributesExtractor<McpRequestInfo, McpResponseInfo> {

        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext, McpRequestInfo request) {
            // Required
            attributes.put(MCP_METHOD_NAME, request.method().jsonRpcName());

            // Conditionally required - only for requests, not notifications
            Object requestId = request.getRequestId();
            if (requestId != null) {
                attributes.put(RpcIncubatingAttributes.RPC_JSONRPC_REQUEST_ID, requestId.toString());
            }

            // Conditionally required - method-specific
            switch (request.method()) {
                case TOOLS_CALL -> {
                    String toolName = request.getToolName();
                    if (toolName != null) {
                        attributes.put(GenAiIncubatingAttributes.GEN_AI_TOOL_NAME, toolName);
                    }
                    attributes.put(GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME, "execute_tool");
                }
                case PROMPTS_GET -> {
                    String promptName = request.getPromptName();
                    if (promptName != null) {
                        attributes.put(GEN_AI_PROMPT_NAME, promptName);
                    }
                }
                case RESOURCES_READ, RESOURCES_SUBSCRIBE, RESOURCES_UNSUBSCRIBE -> {
                    String uri = request.getResourceUri();
                    if (uri != null) {
                        attributes.put(MCP_RESOURCE_URI, uri);
                    }
                }
                default -> {
                }
            }

            // Recommended
            String sessionId = request.getSessionId();
            if (sessionId != null) {
                attributes.put(MCP_SESSION_ID, sessionId);
            }

            String protocolVersion = request.getProtocolVersion();
            if (protocolVersion != null) {
                attributes.put(MCP_PROTOCOL_VERSION, protocolVersion);
            }

            InitialRequest.Transport transport = request.getTransport();
            if (transport != null) {
                if (transport.getNetworkTransport() != null) {
                    attributes.put(NetworkAttributes.NETWORK_TRANSPORT, transport.getNetworkTransport());
                }
                if (transport.protocolName() != null) {
                    attributes.put(NetworkAttributes.NETWORK_PROTOCOL_NAME, transport.protocolName());
                }
                if (transport.protocolVersion() != null) {
                    attributes.put(NetworkAttributes.NETWORK_PROTOCOL_VERSION, transport.protocolVersion());
                }
            }
        }

        @Override
        public void onEnd(AttributesBuilder attributes, Context context,
                McpRequestInfo request, McpResponseInfo response, Throwable error) {
            if (response != null) {
                if (response.toolError()) {
                    attributes.put(ErrorAttributes.ERROR_TYPE, "tool_error");
                } else if (response.jsonRpcErrorCode() != null) {
                    String code = response.jsonRpcErrorCode().toString();
                    attributes.put(ErrorAttributes.ERROR_TYPE, code);
                    attributes.put(RpcIncubatingAttributes.RPC_JSONRPC_ERROR_CODE, Long.valueOf(response.jsonRpcErrorCode()));
                    if (response.jsonRpcErrorMessage() != null) {
                        attributes.put(RpcIncubatingAttributes.RPC_JSONRPC_ERROR_MESSAGE, response.jsonRpcErrorMessage());
                    }
                }
            }
            if (error != null && response == null) {
                attributes.put(ErrorAttributes.ERROR_TYPE, error.getClass().getSimpleName());
            }
        }
    }

    private static class McpSpanStatusExtractor implements SpanStatusExtractor<McpRequestInfo, McpResponseInfo> {
        @Override
        public void extract(SpanStatusBuilder spanStatusBuilder,
                McpRequestInfo request, McpResponseInfo response, Throwable error) {
            if (error != null) {
                spanStatusBuilder.setStatus(StatusCode.ERROR);
            } else if (response != null && (response.toolError() || response.jsonRpcErrorCode() != null)) {
                spanStatusBuilder.setStatus(StatusCode.ERROR);
            } else {
                spanStatusBuilder.setStatus(StatusCode.UNSET);
            }
        }
    }

    private static class McpMetaTextMapGetter implements TextMapGetter<McpRequestInfo> {
        @Override
        public Iterable<String> keys(McpRequestInfo carrier) {
            JsonObject meta = carrier.getMeta();
            return meta != null ? meta.fieldNames() : Collections.emptySet();
        }

        @Override
        public String get(McpRequestInfo carrier, String key) {
            JsonObject meta = carrier.getMeta();
            return meta != null ? meta.getString(key) : null;
        }
    }
}
