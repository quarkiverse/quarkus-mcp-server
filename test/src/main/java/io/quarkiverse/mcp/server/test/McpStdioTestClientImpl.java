package io.quarkiverse.mcp.server.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.Implementation;
import io.quarkiverse.mcp.server.runtime.Messages;
import io.quarkiverse.mcp.server.test.McpAssured.InitResult;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioAssert;
import io.quarkiverse.mcp.server.test.McpAssured.McpStdioTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ServerCapability;
import io.quarkiverse.mcp.server.test.McpAssured.Snapshot;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class McpStdioTestClientImpl extends McpTestClientBase<McpStdioAssert, McpStdioTestClient>
        implements McpStdioTestClient {

    private static final Logger LOG = Logger.getLogger(McpStdioTestClientImpl.class);

    private final List<String> command;
    private final Path workingDirectory;
    private final Map<String, String> environment;
    private final Consumer<String> stderrHandler;

    private volatile Process process;
    private volatile PrintStream processStdin;
    private final McpClientState state;
    private final List<String> stderrLines;
    private final AtomicReference<Consumer<JsonObject>> requestConsumer = new AtomicReference<>();
    private ExecutorService readerExecutor;

    McpStdioTestClientImpl(BuilderImpl builder) {
        super(builder.name, builder.version, builder.protocolVersion, builder.clientCapabilities, null,
                builder.autoPong, null, builder.title, builder.description, builder.websiteUrl, builder.icons,
                builder.openTelemetry);
        this.command = builder.command;
        this.workingDirectory = builder.workingDirectory;
        this.environment = builder.environment;
        this.stderrHandler = builder.stderrHandler;
        this.state = new McpClientState();
        this.stderrLines = new CopyOnWriteArrayList<>();
        LOG.debugf("McpStdioTestClient created with command: %s", command);
    }

    @Override
    public Process process() {
        if (!isConnected()) {
            throw notConnected();
        }
        return process;
    }

    @Override
    public List<String> stderrLines() {
        return stderrLines;
    }

    @Override
    public McpStdioTestClient connect(Consumer<InitResult> assertFunction) {
        if (process != null) {
            throw new IllegalStateException("Client is already connected");
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            if (!environment.isEmpty()) {
                builder.environment().putAll(environment);
            }
            process = builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start MCP server process", e);
        }

        processStdin = new PrintStream(process.getOutputStream(), true);

        readerExecutor = Executors.newFixedThreadPool(2);

        // Read stdout - newline-delimited JSON messages from the server
        readerExecutor.submit(() -> {
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            try {
                String line;
                while ((line = stdout.readLine()) != null) {
                    LOG.debugf("MCP message received: %s", line);
                    JsonObject json = new JsonObject(line);
                    if (json.containsKey("id")) {
                        if (json.containsKey("result") || json.containsKey("error")) {
                            state.responses.add(json);
                        } else {
                            // Request from the server
                            state.requests.add(json);
                            Consumer<JsonObject> c = requestConsumer.get();
                            if (c != null) {
                                c.accept(json);
                            }
                        }
                    } else {
                        state.notifications.add(json);
                    }
                }
            } catch (IOException e) {
                if (process.isAlive()) {
                    throw new IllegalStateException("Error reading from server stdout", e);
                }
                // Process terminated, expected during disconnect
            }
        });

        // Read stderr
        readerExecutor.submit(() -> {
            BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            try {
                String line;
                while ((line = stderr.readLine()) != null) {
                    stderrLines.add(line);
                    stderrHandler.accept(line);
                }
            } catch (IOException e) {
                if (process.isAlive()) {
                    throw new IllegalStateException("Error reading from server stderr", e);
                }
            }
        });

        if (autoPong) {
            requestConsumer.set(m -> {
                String method = m.getString("method");
                if (method != null && "ping".equals(method)) {
                    JsonObject pong = Messages.newResult(m.getValue("id"), new JsonObject());
                    sendAndForget(pong);
                }
            });
        }

        // Send initialize
        JsonObject initMessage = newInitMessage();
        sendAndForget(initMessage);

        JsonObject initResponse = state.waitForResponse(initMessage);
        JsonObject initResult = assertResultResponse(initMessage, initResponse);
        assertNotNull(initResult);

        JsonObject serverInfo = initResult.getJsonObject("serverInfo");
        JsonObject initCapabilities = initResult.getJsonObject("capabilities");
        List<ServerCapability> capabilities = new ArrayList<>();
        if (initCapabilities != null) {
            for (String capability : initCapabilities.fieldNames()) {
                capabilities.add(new ServerCapability(capability, initCapabilities.getJsonObject(capability).getMap()));
            }
        }
        Implementation implementation = Messages.decodeImplementation(serverInfo);
        InitResult r = new InitResult(initResult.getString("protocolVersion"),
                implementation.name(),
                implementation.title(),
                implementation.version(),
                capabilities,
                initResult.getString("instructions"),
                implementation,
                initResult.getJsonObject("_meta"));
        if (assertFunction != null) {
            assertFunction.accept(r);
        }
        this.initResult = r;

        // Send "notifications/initialized"
        JsonObject notification = newMessage("notifications/initialized");
        sendAndForget(notification);

        connected.set(true);
        return this;
    }

    @Override
    public void disconnect() {
        connected.set(false);
        if (processStdin != null) {
            processStdin.close();
        }
        if (process != null && process.isAlive()) {
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        if (readerExecutor != null) {
            readerExecutor.shutdownNow();
        }
        process = null;
        processStdin = null;
    }

    @Override
    public McpStdioAssert when() {
        return new McpStdioAssertImpl();
    }

    @Override
    public McpStdioAssert whenBatch() {
        return new McpStdioAssertBatch();
    }

    @Override
    public void sendAndForget(JsonObject message) {
        LOG.debugf("MCP message sent: %s", message);
        processStdin.println(message.encode());
        processStdin.flush();
    }

    @Override
    protected McpClientState clientState() {
        return state;
    }

    class McpStdioAssertImpl extends McpAssertBase implements McpStdioAssert {

        @Override
        protected McpStdioAssert self() {
            return this;
        }

        @Override
        protected TracingHandle doSend(JsonObject message) {
            sendAndForget(message);
            return null;
        }

    }

    class McpStdioAssertBatch extends McpStdioAssertImpl {

        private final List<JsonObject> requests = new ArrayList<>();

        @Override
        protected TracingHandle doSend(JsonObject message) {
            requests.add(message);
            return null;
        }

        @Override
        public Snapshot thenAssertResults() {
            JsonArray batch = new JsonArray();
            requests.forEach(batch::add);
            LOG.debugf("STDIO batch message sent: %s", batch);
            processStdin.println(batch.encode());
            processStdin.flush();
            return super.thenAssertResults();
        }

    }

    static class BuilderImpl extends McpTestClientBuilder<McpStdioTestClient.Builder>
            implements McpStdioTestClient.Builder {

        private List<String> command;
        private Path workingDirectory;
        private Map<String, String> environment = Map.of();
        private Consumer<String> stderrHandler = System.err::println;

        BuilderImpl() {
            Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            this.workingDirectory = userDir;
            this.command = List.of("java", "-jar",
                    userDir.resolve("target/quarkus-app/quarkus-run.jar").toString());
        }

        @Override
        public McpStdioTestClient.Builder setCommand(String... command) {
            if (command == null) {
                throw mustNotBeNull("command");
            }
            this.command = List.of(command);
            return this;
        }

        @Override
        public McpStdioTestClient.Builder setCommand(List<String> command) {
            if (command == null) {
                throw mustNotBeNull("command");
            }
            this.command = List.copyOf(command);
            return this;
        }

        @Override
        public McpStdioTestClient.Builder setWorkingDirectory(Path dir) {
            if (dir == null) {
                throw mustNotBeNull("dir");
            }
            this.workingDirectory = dir;
            return this;
        }

        @Override
        public McpStdioTestClient.Builder setEnvironment(Map<String, String> env) {
            if (env == null) {
                throw mustNotBeNull("env");
            }
            this.environment = Map.copyOf(env);
            return this;
        }

        @Override
        public McpStdioTestClient.Builder setStderrHandler(Consumer<String> handler) {
            if (handler == null) {
                throw mustNotBeNull("handler");
            }
            this.stderrHandler = handler;
            return this;
        }

        @Override
        public McpStdioTestClient build() {
            return new McpStdioTestClientImpl(this);
        }

    }

}
