package io.quarkiverse.mcp.server.http.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.deployment.FeatureMethodBuildItem;
import io.quarkiverse.mcp.server.deployment.ServerNameBuildItem;
import io.quarkiverse.mcp.server.http.McpParamHeader;
import io.quarkiverse.mcp.server.http.runtime.HttpInputSchemaGenerator;
import io.quarkiverse.mcp.server.http.runtime.HttpMcpServerRecorder;
import io.quarkiverse.mcp.server.http.runtime.McpParamHeaderMetadata;
import io.quarkiverse.mcp.server.http.runtime.McpParamHeaderObserver;
import io.quarkiverse.mcp.server.http.runtime.McpServerEndpoints;
import io.quarkiverse.mcp.server.http.runtime.McpServerEndpoints.McpServerEndpoint;
import io.quarkiverse.mcp.server.http.runtime.McpServerEndpointsLogger;
import io.quarkiverse.mcp.server.http.runtime.SseMcpMessageHandler;
import io.quarkiverse.mcp.server.http.runtime.StreamableHttpMcpMessageHandler;
import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServerBuildTimeConfig;
import io.quarkiverse.mcp.server.http.runtime.config.McpHttpServersBuildTimeConfig;
import io.quarkiverse.mcp.server.runtime.FeatureKey;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.BodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;

public class HttpMcpServerProcessor {

    private static Logger LOG = Logger.getLogger(HttpMcpServerProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("mcp-server-http");
    }

    @BuildStep
    void addBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(
                AdditionalBeanBuildItem.builder()
                        .setUnremovable()
                        .addBeanClasses(SseMcpMessageHandler.class, StreamableHttpMcpMessageHandler.class,
                                McpServerEndpointsLogger.class, HttpInputSchemaGenerator.class,
                                McpParamHeaderObserver.class)
                        .build());
    }

    @BuildStep
    void serverNames(McpHttpServersBuildTimeConfig config, BuildProducer<ServerNameBuildItem> serverNames) {
        for (Map.Entry<String, McpHttpServerBuildTimeConfig> e : config.servers().entrySet()) {
            if (e.getValue().http().enabled()) {
                serverNames.produce(new ServerNameBuildItem(e.getKey()));
            }
        }
    }

    @Record(RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    @BuildStep
    void registerEndpoints(McpServerEndpointsBuildItem mcpServerEndpoints,
            HttpMcpServerRecorder recorder,
            BodyHandlerBuildItem bodyHandler,
            BuildProducer<RouteBuildItem> routes) {
        for (McpServerEndpoint endpoint : mcpServerEndpoints.getEndpoints()) {
            // Streamable HTTP transport
            routes.produce(RouteBuildItem.newFrameworkRoute(endpoint.mcpPath)
                    .withRouteCustomizer(recorder.addBodyHandler(bodyHandler.getHandler()))
                    .withRequestHandler(recorder.createMcpEndpointHandler(endpoint.serverName))
                    .build());
            routes.produce(RouteBuildItem.newFrameworkRoute(endpoint.mcpPath)
                    .withRequestHandler(recorder.createAuthFailureHandler())
                    .asFailureRoute()
                    .build());
            // SSE/HTTP transport
            routes.produce(RouteBuildItem.newFrameworkRoute(endpoint.ssePath)
                    .withRequestHandler(recorder.createSseEndpointHandler(endpoint.mcpPath, endpoint.serverName))
                    .build());
            routes.produce(RouteBuildItem.newFrameworkRoute(endpoint.mcpPath + "/" + "messages/:id")
                    .withRouteCustomizer(recorder.addBodyHandler(bodyHandler.getHandler()))
                    .withRequestHandler(recorder.createMessagesEndpointHandler(endpoint.serverName))
                    .build());
        }
    }

    @Record(STATIC_INIT)
    @BuildStep
    void registerMcpEndpointsBean(McpServerEndpointsBuildItem mcpServerEndpoints, HttpMcpServerRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(McpServerEndpoints.class)
                .scope(Singleton.class)
                .createWith(recorder.createMcpServerEndpoints(mcpServerEndpoints.getEndpoints()))
                .done());
    }

    private static final DotName MCP_PARAM_HEADER = DotName.createSimple(McpParamHeader.class);
    private static final DotName TOOL_ARG = DotName.createSimple(io.quarkiverse.mcp.server.ToolArg.class);

    private static final Set<DotName> ALLOWED_HEADER_TYPES = Set.of(
            DotName.createSimple(String.class),
            DotName.createSimple(Integer.class),
            DotName.createSimple(Long.class),
            DotName.createSimple(Boolean.class));

    @Record(STATIC_INIT)
    @BuildStep
    void registerMcpParamHeaderMetadataBean(List<FeatureMethodBuildItem> featureMethods,
            HttpMcpServerRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        Map<FeatureKey, Map<String, String>> toolHeaders = new HashMap<>();
        for (FeatureMethodBuildItem fm : featureMethods) {
            if (!fm.isTool()) {
                continue;
            }
            Map<String, String> headers = null;
            Set<String> headerNamesLower = null;
            for (MethodParameterInfo param : fm.getMethod().parameters()) {
                AnnotationInstance mcpParamHeader = param.declaredAnnotation(MCP_PARAM_HEADER);
                if (mcpParamHeader == null) {
                    continue;
                }
                String headerName;
                if (mcpParamHeader.value() == null || McpParamHeader.ELEMENT_NAME.equals(mcpParamHeader.value().asString())) {
                    headerName = param.name();
                } else {
                    headerName = mcpParamHeader.value().asString();
                }
                // Validate header name is non-empty
                if (headerName.isEmpty()) {
                    throw new IllegalStateException(
                            "@McpParamHeader value must not be empty [method: %s#%s(), parameter: %s]"
                                    .formatted(fm.getMethod().declaringClass().name(), fm.getMethod().name(), param.name()));
                }
                // Validate header name is a valid HTTP token
                if (!isValidHttpToken(headerName)) {
                    throw new IllegalStateException(
                            "@McpParamHeader value '%s' is not a valid HTTP field-name token [method: %s#%s(), parameter: %s]"
                                    .formatted(headerName, fm.getMethod().declaringClass().name(), fm.getMethod().name(),
                                            param.name()));
                }
                // Validate parameter type is a supported primitive type
                if (!isAllowedHeaderType(param.type())) {
                    throw new IllegalStateException(
                            "@McpParamHeader is only allowed on String, Integer, Long, Boolean, int, long, or boolean parameters"
                                    + " [method: %s#%s(), parameter: %s]"
                                            .formatted(fm.getMethod().declaringClass().name(), fm.getMethod().name(),
                                                    param.name()));
                }
                // Validate uniqueness (case-insensitive) within the tool
                if (headerNamesLower == null) {
                    headerNamesLower = new HashSet<>();
                }
                if (!headerNamesLower.add(headerName.toLowerCase())) {
                    throw new IllegalStateException(
                            "Duplicate @McpParamHeader value '%s' (case-insensitive) in tool method %s#%s()"
                                    .formatted(headerName, fm.getMethod().declaringClass().name(), fm.getMethod().name()));
                }

                if (headers == null) {
                    headers = new HashMap<>();
                }
                String argName = resolveArgName(param);
                headers.put(argName, headerName);
            }
            if (headers != null) {
                for (String serverName : fm.getServers()) {
                    toolHeaders.put(new FeatureKey(fm.getName(), serverName), Map.copyOf(headers));
                }
            }
        }
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(McpParamHeaderMetadata.class)
                .scope(Singleton.class)
                .createWith(recorder.createMcpParamHeaderMetadata(toolHeaders))
                .done());
    }

    private static String resolveArgName(MethodParameterInfo param) {
        AnnotationInstance toolArg = param.declaredAnnotation(TOOL_ARG);
        if (toolArg != null && toolArg.value("name") != null) {
            String name = toolArg.value("name").asString();
            if (!io.quarkiverse.mcp.server.ToolArg.ELEMENT_NAME.equals(name)) {
                return name;
            }
        }
        return param.name();
    }

    static boolean isAllowedHeaderType(org.jboss.jandex.Type type) {
        if (type.kind() == org.jboss.jandex.Type.Kind.PRIMITIVE) {
            PrimitiveType.Primitive primitive = type.asPrimitiveType().primitive();
            return primitive == PrimitiveType.Primitive.INT
                    || primitive == PrimitiveType.Primitive.LONG
                    || primitive == PrimitiveType.Primitive.BOOLEAN;
        }
        return ALLOWED_HEADER_TYPES.contains(type.name());
    }

    static boolean isValidHttpToken(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isTchar(c)) {
                return false;
            }
        }
        return true;
    }

    // RFC 9110 Section 5.6.2: tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
    // "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
    private static boolean isTchar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '!' || c == '#' || c == '$' || c == '%' || c == '&' || c == '\'' || c == '*'
                || c == '+' || c == '-' || c == '.' || c == '^' || c == '_' || c == '`' || c == '|' || c == '~';
    }

    @BuildStep
    McpServerEndpointsBuildItem collectMcpServerEndpoints(McpHttpServersBuildTimeConfig config,
            HttpRootPathBuildItem httpRootPath) {
        List<McpServerEndpoint> endpoints = new ArrayList<>();
        Set<String> rootPaths = new HashSet<>();
        for (Map.Entry<String, McpHttpServerBuildTimeConfig> e : config.servers().entrySet()) {
            String serverName = e.getKey();
            if (!e.getValue().http().enabled()) {
                LOG.debugf("HTTP transport disabled for server [%s]", serverName);
                continue;
            }
            String rootPath = e.getValue().http().rootPath();
            if (!rootPaths.add(rootPath)) {
                throw new IllegalStateException("Multiple server configurations define the same root path: " + rootPath);
            }
            // Streamable HTTP transport, by default /mcp
            String mcpPath = httpRootPath.relativePath(rootPath);
            // SSE/HTTP transport, by default /mcp/sse
            String ssePath = mcpPath.endsWith("/") ? mcpPath + "sse" : mcpPath + "/sse";
            endpoints.add(new McpServerEndpoint(serverName, mcpPath, ssePath));
        }
        return new McpServerEndpointsBuildItem(endpoints);
    }

}
