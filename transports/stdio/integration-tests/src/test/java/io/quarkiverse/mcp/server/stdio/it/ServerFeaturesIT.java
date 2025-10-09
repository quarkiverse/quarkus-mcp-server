package io.quarkiverse.mcp.server.stdio.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@TestMethodOrder(OrderAnnotation.class)
public class ServerFeaturesIT {

    AtomicInteger idGenerator = new AtomicInteger();

    static Process process;
    static PrintStream out;
    static List<String> stderrLines;

    static final BlockingQueue<JsonObject> synchronizer = new LinkedBlockingQueue<>();

    @BeforeAll
    static void startServer() throws IOException {
        System.out.println("Starting server...");
        Path quarkusRunJar = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
                .resolve("target/quarkus-app/quarkus-run.jar");

        ProcessBuilder builder = new ProcessBuilder("java", "-jar", quarkusRunJar.toString());
        process = builder.start();
        out = new PrintStream(process.getOutputStream(), true);
        stderrLines = new CopyOnWriteArrayList<>();

        Executors.newSingleThreadExecutor().submit(new Runnable() {

            @Override
            public void run() {
                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                try {
                    String line;
                    while ((line = stdout.readLine()) != null) {
                        System.out.printf("JSON message received:%n%s%n", line);
                        synchronizer.put(new JsonObject(line));
                    }
                } catch (IOException | InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        Executors.newSingleThreadExecutor().submit(new Runnable() {

            @Override
            public void run() {
                BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                try {
                    String line;
                    while ((line = stderr.readLine()) != null) {
                        System.out.println(line);
                        stderrLines.add(line);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

    }

    @AfterAll
    static void stopServer() {
        if (process != null) {
            process.destroy();
            process = null;
        }
        out = null;
        stderrLines = null;
    }

    @Order(1)
    @Test
    public void testPrompt() {
        initClient();

        JsonObject promptListMessage = newMessage("prompts/list");
        sendMessage(promptListMessage);

        JsonObject promptListResponse = awaitResponse();

        JsonObject promptListResult = assertResponseMessage(promptListMessage, promptListResponse);
        assertNotNull(promptListResult);
        JsonArray prompts = promptListResult.getJsonArray("prompts");
        assertEquals(1, prompts.size());

        assertPrompt(prompts.getJsonObject(0), "code_assist", null, args -> {
            assertEquals(1, args.size());
            JsonObject arg1 = args.getJsonObject(0);
            assertEquals("lang", arg1.getString("name"));
            assertEquals(true, arg1.getBoolean("required"));
        });
        assertPromptMessage("System.out.println(\"Hello world!\");", "code_assist", new JsonObject()
                .put("lang", "java"));

        // Assert that quarkus log is redirected to stderr by default
        assertFalse(stderrLines.isEmpty());
        assertTrue(stderrLines.stream().anyMatch(l -> l.contains("Log from code assist...")));
    }

    @Order(2)
    @Test
    public void testTool() {
        JsonObject toolListMessage = newMessage("tools/list");
        sendMessage(toolListMessage);

        JsonObject toolListResponse = awaitResponse();

        JsonObject toolListResult = assertResponseMessage(toolListMessage, toolListResponse);
        assertNotNull(toolListResult);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(1, tools.size());

        assertTool(tools.getJsonObject(0), "toLowerCase", null, schema -> {
            JsonObject properties = schema.getJsonObject("properties");
            assertEquals(1, properties.size());
            JsonObject valueProperty = properties.getJsonObject("value");
            assertNotNull(valueProperty);
            assertEquals("string", valueProperty.getString("type"));
        });

        assertToolCallAndNotification(
                "loop", "toLowerCase", new JsonObject()
                        .put("value", "LooP"));
    }

    @Order(3)
    @Test
    public void testResource() {
        JsonObject resourceListMessage = newMessage("resources/list");
        sendMessage(resourceListMessage);

        JsonObject resourceListResponse = awaitResponse();

        JsonObject resourceListResult = assertResponseMessage(resourceListMessage, resourceListResponse);
        assertNotNull(resourceListResult);
        JsonArray resources = resourceListResult.getJsonArray("resources");
        assertEquals(1, resources.size());

        assertResource(resources.getJsonObject(0), "alpha", null, "file:///project/alpha", null);

        assertResourceRead(Base64.getMimeEncoder().encodeToString("data".getBytes()), "file:///project/alpha",
                "file:///project/alpha");
    }

    protected JsonObject assertResponseMessage(JsonObject message, JsonObject response) {
        assertEquals(message.getInteger("id"), response.getInteger("id"));
        assertEquals("2.0", response.getString("jsonrpc"));
        return response.getJsonObject("result");
    }

    protected JsonObject newMessage(String method) {
        return new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("id", idGenerator.incrementAndGet());
    }

    private void assertPrompt(JsonObject prompt, String name, String description, Consumer<JsonArray> argumentsAsserter) {
        assertEquals(name, prompt.getString("name"));
        if (description != null) {
            assertEquals(description, prompt.getString("description"));
        }
        if (argumentsAsserter != null) {
            argumentsAsserter.accept(prompt.getJsonArray("arguments"));
        }
    }

    private void assertPromptMessage(String expectedText, String name, JsonObject arguments) {
        JsonObject promptGetMessage = newMessage("prompts/get")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));
        sendMessage(promptGetMessage);

        JsonObject promptGetResponse = awaitResponse();

        JsonObject promptGetResult = assertResponseMessage(promptGetMessage, promptGetResponse);
        assertNotNull(promptGetResult);
        JsonArray messages = promptGetResult.getJsonArray("messages");
        assertEquals(1, messages.size());
        JsonObject message = messages.getJsonObject(0);
        assertEquals("user", message.getString("role"));
        JsonObject content = message.getJsonObject("content");
        assertEquals("text", content.getString("type"));
        assertEquals(expectedText, content.getString("text"));
    }

    private void assertTool(JsonObject tool, String name, String description, Consumer<JsonObject> inputSchemaAsserter) {
        assertEquals(name, tool.getString("name"));
        if (description != null) {
            assertEquals(description, tool.getString("description"));
        }
        if (inputSchemaAsserter != null) {
            inputSchemaAsserter.accept(tool.getJsonObject("inputSchema"));
        }
    }

    private void assertToolCallAndNotification(String expectedText, String name, JsonObject arguments) {
        JsonObject toolGetMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));
        sendMessage(toolGetMessage);

        // Since we're using stdio we need to await the notification first
        JsonObject notification = awaitResponse();
        assertEquals("notifications/tools/list_changed", notification.getString("method"));

        JsonObject toolGetResponse = awaitResponse();

        JsonObject toolGetResult = assertResponseMessage(toolGetMessage, toolGetResponse);
        assertNotNull(toolGetResult);
        JsonArray content = toolGetResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText, textContent.getString("text"));
    }

    private void assertResource(JsonObject resource, String name, String description, String uri, String mimeType) {
        assertEquals(name, resource.getString("name"));
        if (description != null) {
            assertEquals(description, resource.getString("description"));
        }
        assertEquals(uri, resource.getString("uri"));
        if (mimeType != null) {
            assertEquals(description, resource.getString("mimeType"));
        }
    }

    private void assertResourceRead(String expectedBlob, String expectedUri, String uri) {
        JsonObject resourceReadMessage = newMessage("resources/read")
                .put("params", new JsonObject()
                        .put("uri", uri));
        sendMessage(resourceReadMessage);

        JsonObject resourceReadResponse = awaitResponse();

        JsonObject resourceReadResult = assertResponseMessage(resourceReadMessage, resourceReadResponse);
        assertNotNull(resourceReadResult);
        JsonArray contents = resourceReadResult.getJsonArray("contents");
        assertEquals(1, contents.size());
        JsonObject blobContent = contents.getJsonObject(0);
        assertEquals(expectedBlob, blobContent.getString("blob"));
        assertEquals(expectedUri, blobContent.getString("uri"));
    }

    protected void initClient() {
        JsonObject initMessage = newMessage("initialize")
                .put("params",
                        new JsonObject()
                                .put("clientInfo", new JsonObject()
                                        .put("name", "test-client")
                                        .put("version", "1.0"))
                                .put("protocolVersion", "2024-11-05"));

        sendMessage(initMessage);

        JsonObject initResponse = awaitResponse();

        JsonObject initResult = assertResponseMessage(initMessage, initResponse);
        assertNotNull(initResult);
        assertEquals("2024-11-05", initResult.getString("protocolVersion"));

        JsonObject initialized = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("method", "notifications/initialized");
        sendMessage(initialized);
    }

    private void sendMessage(JsonObject message) {
        System.out.printf("JSON message sent:%n%s%n", message);
        out.println(message);
    }

    private JsonObject awaitResponse() {
        try {
            return synchronizer.poll(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
