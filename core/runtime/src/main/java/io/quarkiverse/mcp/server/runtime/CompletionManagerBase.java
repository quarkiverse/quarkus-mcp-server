package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.CompletionManager;
import io.quarkiverse.mcp.server.CompletionManager.CompletionInfo;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public abstract class CompletionManagerBase extends FeatureManagerBase<CompletionResponse, CompletionInfo>
        implements CompletionManager {

    // key = prompt name + "_" + argument name
    protected final ConcurrentMap<String, CompletionInfo> completions;

    protected CompletionManagerBase(Vertx vertx, ObjectMapper mapper, ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation);
        this.completions = new ConcurrentHashMap<>();
    }

    @Override
    public CompletionDefinition newCompletion(String nameReference) {
        return new CompletionDefinitionImpl(nameReference);
    }

    @Override
    public boolean removeCompletion(Predicate<CompletionInfo> filter) {
        return completions.entrySet().removeIf(e -> filter.test(e.getValue()));
    }

    @Override
    Stream<CompletionInfo> infoStream() {
        return completions.values().stream();
    }

    @Override
    public int size() {
        return completions.size();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<CompletionResponse> getInvoker(String id) {
        CompletionInfo completion = completions.get(id);
        if (completion instanceof FeatureInvoker fi) {
            return fi;
        }
        return null;
    }

    protected abstract Feature feature();

    protected abstract void validateReference(String refName, String argumentName);

    IllegalArgumentException completionAlreadyExists(String refName, String argName) {
        return new IllegalArgumentException("A completion for [" + refName + "] with agument [" + argName + "] already exits");
    }

    class CompletionMethod extends FeatureMetadataInvoker<CompletionResponse> implements CompletionManager.CompletionInfo {

        CompletionMethod(FeatureMetadata<CompletionResponse> metadata) {
            super(metadata);
        }

        @Override
        public String name() {
            return metadata.info().name();
        }

        @Override
        public String argumentName() {
            return metadata.info().arguments().stream().filter(FeatureArgument::isParam).findFirst().orElseThrow().name();
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

    class CompletionDefinitionImpl
            extends
            FeatureManagerBase.FeatureDefinitionBase<CompletionInfo, CompletionArguments, CompletionResponse, CompletionDefinitionImpl>
            implements CompletionManager.CompletionDefinition {

        private String argumentName;

        private CompletionDefinitionImpl(String name) {
            super(name);
            setDescription("");
        }

        @Override
        public CompletionDefinition setArgumentName(String argumentName) {
            this.argumentName = argumentName;
            return this;
        }

        @Override
        public CompletionInfo register() {
            validate();
            validateReference(name, argumentName);
            CompletionDefinitionInfo ret = new CompletionDefinitionInfo(name, description, fun, asyncFun,
                    runOnVirtualThread, argumentName);
            String key = ret.name() + "_" + ret.argumentName();
            CompletionInfo existing = completions.putIfAbsent(key, ret);
            if (existing != null) {
                throw completionAlreadyExists(name, argumentName);
            }
            return ret;
        }
    }

    class CompletionDefinitionInfo extends FeatureManagerBase.FeatureDefinitionInfoBase<CompletionArguments, CompletionResponse>
            implements CompletionManager.CompletionInfo {

        protected CompletionDefinitionInfo(String name, String description,
                Function<CompletionArguments, CompletionResponse> fun,
                Function<CompletionArguments, Uni<CompletionResponse>> asyncFun, boolean runOnVirtualThread,
                String argumentName) {
            super(name, description, fun, asyncFun, runOnVirtualThread);
            this.argumentName = argumentName;
        }

        private final String argumentName;

        @Override
        public String argumentName() {
            return argumentName;
        }

        @Override
        public JsonObject asJson() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected CompletionArguments createArguments(ArgumentProviders argumentProviders) {
            return new CompletionArguments(argumentProviders.args().get(argumentName).toString(),
                    argumentProviders.connection(),
                    log(feature().toString().toLowerCase() + ":" + name, name, argumentProviders),
                    new RequestId(argumentProviders.requestId()));
        }

    }
}
