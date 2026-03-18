package io.quarkiverse.mcp.server.schema.validator;

import static io.vertx.json.schema.OutputFormat.Basic;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.runtime.McpRequest;
import io.quarkiverse.mcp.server.runtime.McpRequestValidator;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Draft;
import io.vertx.json.schema.JsonSchema;
import io.vertx.json.schema.JsonSchemaOptions;
import io.vertx.json.schema.OutputUnit;
import io.vertx.json.schema.SchemaRepository;
import io.vertx.json.schema.Validator;

@Singleton
public class JsonSchemaValidator implements McpRequestValidator {

    private static final Logger LOG = Logger.getLogger(JsonSchemaValidator.class);

    @Inject
    Vertx vertx;

    private final SchemaRepository repository = SchemaRepository.create(new JsonSchemaOptions()
            .setBaseUri("https://quarkiverse.io/quarkus-mcp-server"));

    private final ConcurrentMap<ValidatorKey, Validator> validators = new ConcurrentHashMap<>();

    private final Map<McpMethod, String> methodSchemas;

    JsonSchemaValidator() {
        methodSchemas = new EnumMap<>(McpMethod.class);
        methodSchemas.put(McpMethod.INITIALIZE, "InitializeRequest");
        methodSchemas.put(McpMethod.PING, "PingRequest");
        methodSchemas.put(McpMethod.RESOURCES_LIST, "ListResourcesRequest");
        methodSchemas.put(McpMethod.RESOURCE_TEMPLATES_LIST, "ListResourceTemplatesRequest");
        methodSchemas.put(McpMethod.RESOURCES_READ, "ReadResourceRequest");
        methodSchemas.put(McpMethod.RESOURCES_SUBSCRIBE, "SubscribeRequest");
        methodSchemas.put(McpMethod.RESOURCES_UNSUBSCRIBE, "UnsubscribeRequest");
        methodSchemas.put(McpMethod.PROMPTS_LIST, "ListPromptsRequest");
        methodSchemas.put(McpMethod.PROMPTS_GET, "GetPromptRequest");
        methodSchemas.put(McpMethod.TOOLS_LIST, "ListToolsRequest");
        methodSchemas.put(McpMethod.TOOLS_CALL, "CallToolRequest");
        methodSchemas.put(McpMethod.LOGGING_SET_LEVEL, "SetLevelRequest");
        methodSchemas.put(McpMethod.COMPLETION_COMPLETE, "CompleteRequest");
        // notifications
        methodSchemas.put(McpMethod.NOTIFICATIONS_CANCELLED, "CancelledNotification");
        methodSchemas.put(McpMethod.NOTIFICATIONS_INITIALIZED, "InitializedNotification");
        methodSchemas.put(McpMethod.NOTIFICATIONS_PROGRESS, "ProgressNotification");
        methodSchemas.put(McpMethod.NOTIFICATIONS_ROOTS_LIST_CHANGED, "RootsListChangedNotification");
    }

    private static final Future<Boolean> SUCCEEDED_FUTURE = Future.succeededFuture(true);

    @Override
    public <MCP_REQUEST extends McpRequest> Future<Boolean> validate(JsonObject message, MCP_REQUEST mcpRequest,
            McpMethod method) {
        if (!methodSchemas.containsKey(method)) {
            LOG.warnf("Unable to validate schema - unsupported method: %s [server: %s, connection: %s]",
                    method,
                    mcpRequest.serverName(),
                    mcpRequest.connection().id());
            return SUCCEEDED_FUTURE;
        }
        String protocolVersion = mcpRequest.protocolVersion();
        if (protocolVersion == null) {
            LOG.warnf("Unable to validate schema - no protocol version [server: %s, connection: %s]", mcpRequest.serverName(),
                    mcpRequest.connection().id());
            return SUCCEEDED_FUTURE;
        }
        return vertx.executeBlocking(new Callable<>() {
            @Override
            public Boolean call() throws Exception {
                Validator validator = validators.computeIfAbsent(new ValidatorKey(method, protocolVersion),
                        JsonSchemaValidator.this::newValidator);
                OutputUnit result = validator.validate(message);
                if (!result.getValid()) {
                    String msg;
                    if (result.getError() != null) {
                        msg = "Schema validation failed - %s: %s".formatted(methodSchemas.get(method), result.getError());
                    } else if (result.getErrors() != null) {
                        msg = "Schema validation failed - %s: [%s]".formatted(methodSchemas.get(method), result.getErrors()
                                .stream()
                                .filter(Objects::nonNull)
                                .map(e -> e.getError())
                                .collect(Collectors.joining(", ")));
                    } else {
                        msg = "Schema validation failed - %s".formatted(methodSchemas.get(method));
                    }
                    LOG.debug(message);
                    mcpRequest.sender()
                            .sendError(Messages.getId(message), JsonRpcErrorCodes.INVALID_REQUEST,
                                    msg);
                    return false;
                }
                return true;
            }
        });
    }

    private record ValidatorKey(McpMethod method, String protocolVersion) {
    }

    private Validator newValidator(ValidatorKey key) {
        String mcpSpecUri = "https://modelcontextprotocol.io/spec/" + key.protocolVersion();
        JsonSchema schema = repository.find(mcpSpecUri);
        if (schema == null) {
            schema = loadSchema(key.protocolVersion());
            repository.dereference(mcpSpecUri, schema);
        }

        // When a schema does not include a $schema field, it defaults to JSON Schema 2020-12
        String schemaDraft = schema.get("$schema");
        Draft draft = schemaDraft != null ? Draft.fromIdentifier(schemaDraft) : Draft.DRAFT202012;

        // Determine the definitions key based on the schema draft version
        String defsKey = schema.containsKey("$defs") ? "$defs" : "definitions";

        String pointer = mcpSpecUri + "#/" + defsKey + "/" + methodSchemas.get(key.method());
        JsonSchema methodSchema = repository.find(pointer);

        if (methodSchema == null) {
            throw new IllegalStateException("Method schema not found: " + pointer);
        }

        return repository.validator(methodSchema,
                new JsonSchemaOptions()
                        .setOutputFormat(Basic)
                        .setDraft(draft));
    }

    private JsonSchema loadSchema(String protocolVersion) {
        String schemaResource = "mcp_schema_" + protocolVersion + ".json";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(schemaResource);
        if (in == null) {
            throw new IllegalStateException("Schema resource not found: " + schemaResource);
        }
        try (in) {
            JsonObject schemaJson = new JsonObject(Buffer.buffer(in.readAllBytes()));
            JsonSchema schema = JsonSchema.of(schemaJson);
            return schema;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
