package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.InitManager;
import io.quarkiverse.mcp.server.InitManager.InitInfo;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@Singleton
public class InitManagerImpl extends FeatureManagerBase<Void, InitInfo> implements InitManager {

    final ConcurrentMap<String, InitInfo> inits;

    InitManagerImpl(McpMetadata metadata, Vertx vertx, ObjectMapper mapper, ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation);
        this.inits = new ConcurrentHashMap<>();
        for (FeatureMetadata<Void> init : metadata.inits()) {
            this.inits.put(init.info().name(), new InitMethod(init));
        }
    }

    @Override
    Stream<InitInfo> infoStream() {
        return inits.values().stream();
    }

    @Override
    public int size() {
        return inits.size();
    }

    @Override
    public InitInfo getInit(String name) {
        return inits.get(Objects.requireNonNull(name));
    }

    @Override
    public InitDefinition newInit(String name) {
        if (inits.containsKey(name)) {
            throw initAlreadyExists(name);
        }
        return new InitDefinitionImpl(name);
    }

    @Override
    public InitInfo removeInit(String name) {
        return inits.remove(name);
    }

    IllegalArgumentException initAlreadyExists(String name) {
        return new IllegalArgumentException("An initializer with name [" + name + "] already exits");
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<Void> getInvoker(String id) {
        InitInfo init = inits.get(id);
        if (init instanceof FeatureInvoker fi) {
            return fi;
        }
        return null;
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Invalid init name: " + id, JsonRPC.INVALID_PARAMS);
    }

    class InitMethod extends FeatureMetadataInvoker<Void> implements InitManager.InitInfo {

        private InitMethod(FeatureMetadata<Void> metadata) {
            super(metadata);
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
        public boolean isMethod() {
            return true;
        }

        @Override
        public JsonObject asJson() {
            throw new UnsupportedOperationException();
        }

    }

    class InitDefinitionImpl
            extends FeatureManagerBase.FeatureDefinitionBase<InitInfo, InitArguments, Void, InitDefinitionImpl>
            implements InitManager.InitDefinition {

        private InitDefinitionImpl(String name) {
            super(name);
        }

        @Override
        public InitInfo register() {
            validate(false);
            InitDefinitionInfo ret = new InitDefinitionInfo(name, description, fun, asyncFun,
                    runOnVirtualThread);
            InitInfo existing = inits.putIfAbsent(name, ret);
            if (existing != null) {
                throw initAlreadyExists(name);
            }
            return ret;
        }
    }

    class InitDefinitionInfo extends FeatureManagerBase.FeatureDefinitionInfoBase<InitArguments, Void>
            implements InitManager.InitInfo {

        private InitDefinitionInfo(String name, String description, Function<InitArguments, Void> fun,
                Function<InitArguments, Uni<Void>> asyncFun, boolean runOnVirtualThread) {
            super(name, description, fun, asyncFun, runOnVirtualThread);
        }

        @Override
        public JsonObject asJson() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected InitArguments createArguments(ArgumentProviders argumentProviders) {
            return new InitArguments(argumentProviders.connection(),
                    log(Feature.TOOL.toString().toLowerCase() + ":" + name, name, argumentProviders));
        }

    }

}
