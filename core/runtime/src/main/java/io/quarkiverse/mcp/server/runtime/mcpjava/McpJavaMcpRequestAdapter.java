package io.quarkiverse.mcp.server.runtime.mcpjava;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mcpjava.server.Icon;
import org.mcpjava.server.ImplementationInfo;
import org.mcpjava.server.McpRequest;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.InitialRequest;
import io.quarkiverse.mcp.server.runtime.ArgumentProviders;
import io.quarkiverse.mcp.server.runtime.McpConnectionBase;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.vertx.core.json.JsonObject;

public class McpJavaMcpRequestAdapter implements McpRequest {

    private final ArgumentProviders argProviders;

    McpJavaMcpRequestAdapter(ArgumentProviders argProviders) {
        this.argProviders = argProviders;
    }

    public static McpJavaMcpRequestAdapter from(ArgumentProviders argProviders) {
        return new McpJavaMcpRequestAdapter(argProviders);
    }

    @Override
    public Object id() {
        return argProviders.requestId();
    }

    @Override
    public Optional<String> sessionId() {
        McpConnectionBase connection = argProviders.connection();
        return connection.isTransient() ? Optional.empty() : Optional.of(connection.id());
    }

    @Override
    public String protocolVersion() {
        return argProviders.connection().initialRequest().protocolVersion().toString();
    }

    @Override
    public Map<String, Object> rawClientCapabilities() {
        InitialRequest initialRequest = argProviders.connection().initialRequest();
        Map<String, Object> capabilities = new HashMap<>();
        for (ClientCapability cap : initialRequest.clientCapabilities()) {
            capabilities.put(cap.name(), cap.properties().isEmpty() ? Map.of() : cap.properties());
        }
        return capabilities;
    }

    @Override
    public ImplementationInfo clientInfo() {
        Implementation impl = argProviders.connection().initialRequest().implementation();
        return new ImplementationInfoAdapter(impl);
    }

    @Override
    public Map<String, Object> metadata() {
        JsonObject params = Messages.getParams(argProviders.rawMessage());
        if (params != null) {
            JsonObject meta = params.getJsonObject("_meta");
            if (meta != null) {
                return meta.getMap();
            }
        }
        return Map.of();
    }

    record ImplementationInfoAdapter(Implementation delegate) implements ImplementationInfo {

        @Override
        public List<Icon> icons() {
            return delegate.icons().stream()
                    .map(IconAdapter::new)
                    .map(Icon.class::cast)
                    .toList();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String title() {
            return delegate.title() != null ? delegate.title() : delegate.name();
        }

        @Override
        public String version() {
            return delegate.version();
        }

        @Override
        public Optional<String> description() {
            return Optional.ofNullable(delegate.description());
        }

        @Override
        public Optional<String> websiteUrl() {
            return Optional.ofNullable(delegate.websiteUrl());
        }
    }

    record IconAdapter(io.quarkiverse.mcp.server.Icon delegate) implements Icon {

        @Override
        public String src() {
            return delegate.src();
        }

        @Override
        public Optional<String> mimeType() {
            return Optional.ofNullable(delegate.mimeType());
        }

        @Override
        public List<String> sizes() {
            return delegate.sizes() != null ? delegate.sizes() : List.of();
        }

        @Override
        public Optional<Theme> theme() {
            if (delegate.theme() == null) {
                return Optional.empty();
            }
            return Optional.of(Theme.valueOf(delegate.theme().name()));
        }
    }
}
