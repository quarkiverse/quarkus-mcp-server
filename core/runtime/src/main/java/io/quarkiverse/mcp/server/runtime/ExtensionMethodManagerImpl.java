package io.quarkiverse.mcp.server.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.runtime.config.McpServersRuntimeConfig;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Registry and dispatcher for custom JSON-RPC methods contributed by MCP extensions
 * ({@link io.quarkiverse.mcp.server.McpExtensionMethod}).
 * <p>
 * Unlike tool arguments (which live under {@code params.arguments}), extension-method arguments are bound directly from the
 * request {@code params} object - see {@link ExtensionMethod#beforeCall(FeatureExecutionContext)}.
 */
@Singleton
public class ExtensionMethodManagerImpl extends FeatureManagerBase<Object, FeatureInfo> {

    final ConcurrentMap<FeatureKey, FeatureInfo> extensionMethods;

    ExtensionMethodManagerImpl(McpMetadata metadata,
            Vertx vertx,
            ObjectMapper mapper,
            ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation,
            ServerRequests serverRequests,
            CancellationRequests cancellationRequests,
            McpServersRuntimeConfig config) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, serverRequests, cancellationRequests,
                config, metadata);
        this.extensionMethods = new ConcurrentHashMap<>();
        for (FeatureMetadata<Object> method : metadata.extensionMethods()) {
            ExtensionMethod extensionMethod = new ExtensionMethod(method);
            for (String server : method.info().serverNames()) {
                this.extensionMethods.put(new FeatureKey(method.info().name(), server), extensionMethod);
            }
        }
    }

    /**
     * @return {@code true} if a method with the given JSON-RPC name is registered for the given server
     */
    boolean isKnownMethod(String method, String serverName) {
        return extensionMethods.containsKey(new FeatureKey(method, serverName));
    }

    @Override
    Stream<FeatureInfo> infos() {
        return extensionMethods.values().stream().distinct();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<Object> getInvoker(String id, McpRequest mcpRequest, JsonObject message) {
        FeatureInfo info = extensionMethods.get(new FeatureKey(id, mcpRequest.serverName()));
        if (info instanceof FeatureInvoker fi) {
            return fi;
        }
        return null;
    }

    @Override
    protected McpException notFound(String id, McpProtocolVersion version) {
        return new McpException("Unsupported method: " + id, JsonRpcErrorCodes.METHOD_NOT_FOUND);
    }

    class ExtensionMethod extends FeatureMetadataInvoker<Object> implements FeatureInfo {

        private ExtensionMethod(FeatureMetadata<Object> metadata) {
            super(metadata, null);
        }

        @Override
        public Uni<JsonObject> beforeCall(FeatureExecutionContext context) {
            // Extension-method arguments are bound directly from the request "params" object
            // (there is no "arguments" wrapper as in tools/call)
            JsonObject params = Messages.getParams(context.message());
            if (params == null) {
                return Uni.createFrom().nullItem();
            }
            return Uni.createFrom().item(params);
        }

        @Override
        public String name() {
            return metadata.info().name();
        }

        @Override
        public String description() {
            return metadata.info().description();
        }

        @Override
        public Set<String> serverNames() {
            return metadata.info().serverNames();
        }

        @Override
        public boolean isMethod() {
            return true;
        }

        @Override
        public JsonObject asJson() {
            // Extension methods are not listable
            throw new UnsupportedOperationException();
        }

    }

}
