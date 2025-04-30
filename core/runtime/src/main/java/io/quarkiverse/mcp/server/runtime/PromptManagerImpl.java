package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptManager.PromptInfo;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Singleton
public class PromptManagerImpl extends FeatureManagerBase<PromptResponse, PromptInfo> implements PromptManager {

    final ConcurrentMap<String, PromptInfo> prompts;

    PromptManagerImpl(McpMetadata metadata, Vertx vertx, ObjectMapper mapper, ConnectionManager connectionManager,
            Instance<CurrentIdentityAssociation> currentIdentityAssociation, ResponseHandlers responseHandlers) {
        super(vertx, mapper, connectionManager, currentIdentityAssociation, responseHandlers);
        this.prompts = new ConcurrentHashMap<>();
        for (FeatureMetadata<PromptResponse> f : metadata.prompts()) {
            this.prompts.put(f.info().name(), new PromptMethod(f));
        }
    }

    @Override
    Stream<PromptInfo> infoStream() {
        return prompts.values().stream();
    }

    @Override
    public int size() {
        return prompts.size();
    }

    @Override
    public PromptInfo getPrompt(String name) {
        return prompts.get(Objects.requireNonNull(name));
    }

    @Override
    public PromptDefinition newPrompt(String name) {
        if (prompts.containsKey(name)) {
            throw promptWithNameAlreadyExists(name);
        }
        return new PromptDefinitionImpl(name);
    }

    @Override
    public PromptInfo removePrompt(String name) {
        return prompts.computeIfPresent(name, (key, value) -> {
            if (!value.isMethod()) {
                notifyConnections("notifications/prompts/list_changed");
                return null;
            }
            return value;
        });
    }

    @Override
    public boolean isEmpty() {
        return prompts.isEmpty();
    }

    IllegalArgumentException promptWithNameAlreadyExists(String name) {
        return new IllegalArgumentException("A prompt with name [" + name + "] already exits");
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Invalid prompt name: " + id, JsonRPC.INVALID_PARAMS);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected FeatureInvoker<PromptResponse> getInvoker(String id) {
        PromptInfo prompt = prompts.get(id);
        if (prompt instanceof FeatureInvoker fi) {
            return fi;
        }
        return null;
    }

    class PromptMethod extends FeatureMetadataInvoker<PromptResponse> implements PromptManager.PromptInfo {

        private PromptMethod(FeatureMetadata<PromptResponse> metadata) {
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
        public List<PromptArgument> arguments() {
            return metadata.info().serializedArguments().stream()
                    .map(a -> new PromptArgument(a.name(), a.description(), a.required(), a.defaultValue())).toList();
        }

        @Override
        public JsonObject asJson() {
            return metadata.asJson();
        }

    }

    class PromptDefinitionImpl
            extends FeatureManagerBase.FeatureDefinitionBase<PromptInfo, PromptArguments, PromptResponse, PromptDefinitionImpl>
            implements PromptManager.PromptDefinition {

        private final List<PromptArgument> arguments;

        PromptDefinitionImpl(String name) {
            super(name);
            this.arguments = new ArrayList<>();
        }

        @Override
        public PromptDefinition addArgument(String name, String description, boolean required, String defaultValue) {
            arguments.add(new PromptArgument(name, description, required, defaultValue));
            return this;
        }

        @Override
        public PromptInfo register() {
            validate();
            PromptDefinitionInfo ret = new PromptDefinitionInfo(name, description, fun, asyncFun,
                    runOnVirtualThread, arguments);
            PromptInfo existing = prompts.putIfAbsent(name, ret);
            if (existing != null) {
                throw promptWithNameAlreadyExists(name);
            } else {
                notifyConnections(McpMessageHandler.NOTIFICATIONS_PROMPTS_LIST_CHANGED);
            }
            return ret;
        }
    }

    class PromptDefinitionInfo extends FeatureManagerBase.FeatureDefinitionInfoBase<PromptArguments, PromptResponse>
            implements PromptManager.PromptInfo {

        private final List<PromptArgument> arguments;

        private PromptDefinitionInfo(String name, String description, Function<PromptArguments, PromptResponse> fun,
                Function<PromptArguments, Uni<PromptResponse>> asyncFun, boolean runOnVirtualThread,
                List<PromptArgument> arguments) {
            super(name, description, fun, asyncFun, runOnVirtualThread);
            this.arguments = List.copyOf(arguments);
        }

        @Override
        public List<PromptArgument> arguments() {
            return arguments;
        }

        @Override
        protected PromptArguments createArguments(ArgumentProviders argumentProviders) {
            Map<String, String> args = new HashMap<>();
            for (Entry<String, Object> e : argumentProviders.args().entrySet()) {
                args.put(e.getKey(), e.getValue().toString());
            }
            for (PromptArgument a : arguments) {
                if (a.defaultValue() != null && !args.containsKey(a.name())) {
                    args.put(a.name(), a.defaultValue());
                }
            }
            return new PromptArguments(args, argumentProviders.connection(),
                    log(Feature.PROMPT.toString().toLowerCase() + ":" + name, name, argumentProviders),
                    new RequestId(argumentProviders.requestId()),
                    ProgressImpl.from(argumentProviders),
                    RootsImpl.from(argumentProviders),
                    SamplingImpl.from(argumentProviders));
        }

        @Override
        public JsonObject asJson() {
            JsonObject prompt = new JsonObject()
                    .put("name", name())
                    .put("description", description());
            JsonArray arguments = new JsonArray();
            for (PromptArgument a : this.arguments) {
                arguments.add(new JsonObject()
                        .put("name", a.name())
                        .put("description", a.description())
                        .put("required", a.required()));
            }
            prompt.put("arguments", arguments);
            return prompt;
        }

    }

}
