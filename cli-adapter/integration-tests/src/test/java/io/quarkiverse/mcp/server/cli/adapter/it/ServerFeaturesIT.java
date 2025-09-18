package io.quarkiverse.mcp.server.cli.adapter.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        ProcessBuilder builder = new ProcessBuilder("java", "-jar", quarkusRunJar.toString(), "--mcp");
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
    public void testTool() {
        initClient();
        JsonObject toolListMessage = newMessage("tools/list");
        sendMessage(toolListMessage);

        JsonObject toolListResponse = awaitResponse();

        JsonObject toolListResult = assertResponseMessage(toolListMessage, toolListResponse);
        assertNotNull(toolListResult);
        JsonArray tools = toolListResult.getJsonArray("tools");
        assertEquals(1, tools.size());

        assertTool(tools.getJsonObject(0), "codeservicecommand", null, schema -> {
            JsonObject properties = schema.getJsonObject("properties");
            assertEquals(1, properties.size());
            JsonObject valueProperty = properties.getJsonObject("language");
            assertNotNull(valueProperty);
            assertEquals("string", valueProperty.getString("type"));
        });

        assertToolCall("System.out.println(\"Hello world!\");", "codeservicecommand", new JsonObject()
                .put("language", "java"));
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

    private void assertTool(JsonObject tool, String name, String description, Consumer<JsonObject> inputSchemaAsserter) {
        assertEquals(name, tool.getString("name"));
        if (description != null) {
            assertEquals(description, tool.getString("description"));
        }
        if (inputSchemaAsserter != null) {
            inputSchemaAsserter.accept(tool.getJsonObject("inputSchema"));
        }
    }

    private void assertToolCall(String expectedText, String name, JsonObject arguments) {
        JsonObject toolGetMessage = newMessage("tools/call")
                .put("params", new JsonObject()
                        .put("name", name)
                        .put("arguments", arguments));
        sendMessage(toolGetMessage);

        JsonObject toolGetResponse = awaitResponse();

        JsonObject toolGetResult = assertResponseMessage(toolGetMessage, toolGetResponse);
        assertNotNull(toolGetResult);
        JsonArray content = toolGetResult.getJsonArray("content");
        assertEquals(1, content.size());
        JsonObject textContent = content.getJsonObject(0);
        assertEquals("text", textContent.getString("type"));
        assertEquals(expectedText.strip(), textContent.getString("text").strip());
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
            return synchronizer.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
