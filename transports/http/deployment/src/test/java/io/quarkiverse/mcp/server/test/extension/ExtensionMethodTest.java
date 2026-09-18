package io.quarkiverse.mcp.server.test.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Cancellation;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.McpExtension;
import io.quarkiverse.mcp.server.McpExtensionMethod;
import io.quarkiverse.mcp.server.McpExtensionMethodArg;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.RawMessage;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ExtensionMethodTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(root -> root.addClasses(SkillsExtension.class));

    @Test
    public void testSse() {
        assertSkills(McpAssured.newConnectedSseClient(), false);
    }

    @Test
    public void testStreamable() {
        assertSkills(McpAssured.newConnectedStreamableClient(), false);
    }

    @Test
    public void testStreamableStateless() {
        // In the stateless protocol each request carries the client info/capabilities in _meta
        assertSkills(McpAssured.newStreamableClient().setStateless().build().connect(), true);
    }

    static <A extends McpAssert<A>> void assertSkills(McpTestClient<A, ?> client, boolean stateless) {
        try (client) {
            // skills/list -> { "skills": [...] } (sync POJO result)
            client.when()
                    .message(newRequest(client, "skills/list", null, stateless))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertNotNull(result);
                        JsonArray skills = result.getJsonArray("skills");
                        assertEquals(2, skills.size());
                        assertEquals("code-review", skills.getJsonObject(0).getString("uri"));
                        assertEquals("Reviews code", skills.getJsonObject(0).getString("description"));
                    })
                    .send()
                    .thenAssertResults();

            // skills/get with a top-level "uri" param (NOT params.arguments) -> single skill
            client.when()
                    .message(newRequest(client, "skills/get", new JsonObject().put("uri", "code-review"), stateless))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertEquals("code-review", result.getString("uri"));
                        assertEquals("Skill for code-review", result.getString("description"));
                    })
                    .send()
                    .thenAssertResults();

            // skills/getAsync returns a Uni -> async result mapper
            client.when()
                    .message(newRequest(client, "skills/getAsync", new JsonObject().put("uri", "summarize"), stateless))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertEquals("summarize", result.getString("uri"));
                        assertEquals("Async skill for summarize", result.getString("description"));
                    })
                    .send()
                    .thenAssertResults();

            // A feature-method provider (McpConnection) is injected
            client.when()
                    .message(newRequest(client, "skills/connection", null, stateless))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertNotNull(result.getString("connectionId"));
                    })
                    .send()
                    .thenAssertResults();

            // Missing required param -> -32602
            client.when()
                    .message(newRequest(client, "skills/get", null, stateless))
                    .withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.INVALID_PARAMS, error.code()))
                    .send()
                    .thenAssertResults();

            // @McpExtensionMethodArg overrides the wire name of the param (skill-uri, not uri)
            client.when()
                    .message(newRequest(client, "skills/rename", new JsonObject().put("skill-uri", "code-review"),
                            stateless))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertEquals("code-review", result.getString("uri"));
                        assertEquals("Renamed code-review", result.getString("description"));
                    })
                    .send()
                    .thenAssertResults();

            // The defaultValue is used when the param is absent
            client.when()
                    .message(newRequest(client, "skills/withDefault", null, stateless))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertEquals("default-skill", result.getString("uri"));
                    })
                    .send()
                    .thenAssertResults();

            // An (immutable) Map result is mapped to a JsonObject and enriched by the sender (resultType: "complete")
            client.when()
                    .message(newRequest(client, "skills/map", new JsonObject().put("uri", "code-review"), stateless))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertEquals("code-review", result.getString("uri"));
                        assertEquals("Map skill for code-review", result.getString("description"));
                        assertEquals("complete", result.getString("resultType"));
                    })
                    .send()
                    .thenAssertResults();

            // A null result is mapped to an empty object and still enriched by the sender
            client.when()
                    .message(newRequest(client, "skills/nullResult", null, stateless))
                    .withAssert(response -> {
                        JsonObject result = response.getJsonObject("result");
                        assertNotNull(result);
                        assertNull(result.getString("uri"));
                        assertEquals("complete", result.getString("resultType"));
                    })
                    .send()
                    .thenAssertResults();

            // A business error (McpException) is propagated with its error code
            client.when()
                    .message(newRequest(client, "skills/fail", null, stateless))
                    .withErrorAssert(error -> {
                        assertEquals(JsonRpcErrorCodes.INVALID_REQUEST, error.code());
                        assertEquals("boom", error.message());
                    })
                    .send()
                    .thenAssertResults();

            // An unknown extension method -> -32601
            client.when()
                    .message(newRequest(client, "skills/unknown", null, stateless))
                    .withErrorAssert(error -> assertEquals(JsonRpcErrorCodes.METHOD_NOT_FOUND, error.code()))
                    .send()
                    .thenAssertResults();
        }
    }

    static JsonObject newRequest(McpTestClient<?, ?> client, String method, JsonObject params, boolean stateless) {
        JsonObject request = client.newRequest(method);
        if (params != null) {
            request.put("params", params);
        }
        if (stateless) {
            McpAssured.injectStatelessMeta(request);
        }
        return request;
    }

    @McpExtension(id = "io.modelcontextprotocol/skills")
    public static class SkillsExtension {

        @McpExtensionMethod("skills/list")
        public SkillsResult list() {
            return new SkillsResult(List.of(new Skill("code-review", "Reviews code"),
                    new Skill("summarize", "Summarizes text")));
        }

        @McpExtensionMethod("skills/get")
        public Skill get(String uri) {
            return new Skill(uri, "Skill for " + uri);
        }

        @McpExtensionMethod("skills/getAsync")
        public Uni<Skill> getAsync(String uri) {
            return Uni.createFrom().item(new Skill(uri, "Async skill for " + uri));
        }

        @McpExtensionMethod("skills/rename")
        public Skill rename(@McpExtensionMethodArg(name = "skill-uri") String uri) {
            return new Skill(uri, "Renamed " + uri);
        }

        @McpExtensionMethod("skills/withDefault")
        // A defaultValue implies required = false (no need to set it explicitly)
        public Skill withDefault(@McpExtensionMethodArg(defaultValue = "default-skill") String uri) {
            return new Skill(uri, "Skill for " + uri);
        }

        @McpExtensionMethod("skills/connection")
        public JsonObject connection(McpConnection connection, McpLog log, RawMessage rawMessage, Meta meta,
                RequestId requestId, Progress progress, Roots roots, Sampling sampling, Cancellation cancellation) {
            // Verify that the common feature-method providers are bound for extension methods
            Objects.requireNonNull(connection);
            Objects.requireNonNull(log);
            Objects.requireNonNull(rawMessage);
            Objects.requireNonNull(meta);
            Objects.requireNonNull(requestId);
            Objects.requireNonNull(progress);
            Objects.requireNonNull(roots);
            Objects.requireNonNull(sampling);
            Objects.requireNonNull(cancellation);
            return new JsonObject().put("connectionId", connection.id());
        }

        @McpExtensionMethod("skills/map")
        public Map<String, Object> map(String uri) {
            return Map.of("uri", uri, "description", "Map skill for " + uri);
        }

        @McpExtensionMethod("skills/nullResult")
        public Skill nullResult() {
            return null;
        }

        @McpExtensionMethod("skills/fail")
        public Skill fail() {
            throw new McpException("boom", JsonRpcErrorCodes.INVALID_REQUEST);
        }
    }

    public record Skill(String uri, String description) {
    }

    public record SkillsResult(List<Skill> skills) {
    }

}
