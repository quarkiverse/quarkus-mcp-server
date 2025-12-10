package io.quarkiverse.mcp.server.test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.common.annotation.CheckReturnValue;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

/**
 * A set of convenient test utils for MCP servers.
 *
 * @see #newSseClient()
 * @see #newStreamableClient()
 */
public class McpAssured {

    public static final String PING = "ping";
    public static final String TOOLS_LIST = "tools/list";
    public static final String TOOLS_CALL = "tools/call";
    public static final String PROMPTS_LIST = "prompts/list";
    public static final String PROMPTS_GET = "prompts/get";
    public static final String RESOURCES_LIST = "resources/list";
    public static final String RESOURCES_TEMPLATES_LIST = "resources/templates/list";
    public static final String RESOURCES_READ = "resources/read";
    public static final String COMPLETION_COMPLETE = "completion/complete";

    /**
     * The base URI is used by HTTP-based client implementations.
     * <p>
     * The URI set by {@link McpSseTestClient.Builder#setBaseUri(URI)}, {@link McpStreamableTestClient.Builder#setBaseUri(URI)},
     * or {@link McpWebSocketTestClient.Builder#setBaseUri(URI)} takes precedence over this value.
     *
     * @see McpSseTestClient.Builder#setBaseUri(URI)
     * @see McpStreamableTestClient.Builder#setBaseUri(URI)
     * @see McpWebSocketTestClient.Builder#setBaseUri(URI)
     */
    public static URI baseUri;

    /**
     *
     * @return a new SSE test client builder
     */
    public static McpSseTestClient.Builder newSseClient() {
        return new McpSseTestClientImpl.BuilderImpl();
    }

    /**
     *
     * @return a connected SSE test client
     */
    public static McpSseTestClient newConnectedSseClient() {
        return newSseClient().build().connect();
    }

    /**
     *
     * @return a new streamable HTTP test client builder
     */
    public static McpStreamableTestClient.Builder newStreamableClient() {
        return new McpStreamableTestClientImpl.BuilderImpl();
    }

    /**
     *
     * @return a connected streamable HTTP test client
     */
    public static McpStreamableTestClient newConnectedStreamableClient() {
        return newStreamableClient().build().connect();
    }

    /**
     *
     * @return a new WebSocket test client builder
     */
    public static McpWebSocketTestClient.Builder newWebSocketClient() {
        return new McpWebSocketTestClientImpl.BuilderImpl();
    }

    /**
     *
     * @return a connected WebSocket test client
     */
    public static McpWebSocketTestClient newConnectedWebSocketClient() {
        return newWebSocketClient().build().connect();
    }

    public interface McpTestClient<ASSERT extends McpAssert<ASSERT>, CLIENT extends McpTestClient<ASSERT, CLIENT>> {

        /**
         *
         * @return {@code true} if connected, {@code false} otherwise
         */
        boolean isConnected();

        /**
         *
         * @return the result of the {@code initialize} operation or {@code null} if not connected
         */
        InitResult initResult();

        /**
         * Connect the client and perform the MCP initialization.
         *
         * @return the client
         */
        default CLIENT connect() {
            return connect(null);
        }

        /**
         * Connect the client and perform the MCP initialization.
         *
         * @param assertFunction
         * @return the client
         */
        CLIENT connect(Consumer<InitResult> assertFunction);

        /**
         * Disconnect the client and terminate the MCP connection.
         * <p>
         * Does not necessarily terminate the session on the server.
         */
        void disconnect();

        /**
         * Create a group of MCP requests and corresponding assert functions.
         * <p>
         * The MCP requests are sent immediately but the responses are not processed and the assert functions are not used until
         * the {@link McpAssert#thenAssertResults()} method is called.
         *
         * @return the assert group
         * @see McpAssert#thenAssertResults()
         */
        ASSERT when();

        /**
         * Create a group of MCP requests and corresponding assert functions.
         * <p>
         * The MCP requests are sent in a batch, the responses are processed and the assert functions are used when the
         * {@link McpAssert#thenAssertResults()} method is called.
         *
         * @return the assert group
         * @see McpAssert#thenAssertResults()
         */
        ASSERT whenBatch();

        /**
         * @param method
         * @return a new message
         */
        JsonObject newMessage(String method);

        /**
         *
         * @param method
         * @return a new request message
         */
        JsonObject newRequest(String method);

        /**
         *
         * @return a new {@code initialize} message
         */
        JsonObject newInitMessage();

        /**
         * Sends the message without waiting for the result.
         *
         * @param message
         */
        void sendAndForget(JsonObject message);

        /**
         *
         * @return the current snapshot
         */
        Snapshot snapshot();

        /**
         * Wait until the specified number of notifications is received.
         *
         * @param count
         * @return the current snapshot
         */
        Snapshot waitForNotifications(int count);

        /**
         * Wait until the specified number of requests is received.
         *
         * @param count
         * @return the current snapshot
         */
        Snapshot waitForRequests(int count);

        /**
         * Wait until the response for the specified request is received.
         *
         * @param request
         * @return the reponse
         */
        JsonObject waitForResponse(JsonObject request);

        /**
         * @param <BUILDER>
         */
        public interface Builder<BUILDER extends Builder<BUILDER>> {

            /**
             *
             * @param clientName
             * @return self
             */
            BUILDER setName(String clientName);

            /**
             *
             * @param clientVersion
             * @return self
             */
            BUILDER setVersion(String clientVersion);

            /**
             *
             * @param protocolVersion
             * @return self
             */
            BUILDER setProtocolVersion(String protocolVersion);

            /**
             *
             * @param capabilities
             * @return self
             */
            BUILDER setClientCapabilities(ClientCapability... capabilities);

            /**
             *
             * @param val If {@code true} (default) then the client automatically responds to ping requests from the server
             * @return self
             */
            BUILDER setAutoPong(boolean val);

        }

    }

    /**
     * A test client that leverages the SSE transport.
     */
    public interface McpSseTestClient
            extends McpTestClient<McpSseAssert, McpSseTestClient> {

        /**
         *
         * @return the SSE endpoint
         */
        URI sseEndpoint();

        /**
         *
         * @return the current MCP message endpoint
         */
        URI messageEndpoint();

        public interface Builder extends McpTestClient.Builder<Builder> {

            /**
             *
             * @param username
             * @param password
             * @return self
             */
            Builder setBasicAuth(String username, String password);

            /**
             *
             * @param additionalHeaders
             * @return self
             */
            Builder setAdditionalHeaders(Function<JsonObject, MultiMap> additionalHeaders);

            /**
             *
             * @param baseUri
             * @return self
             * @see McpAssured#baseUri
             */
            Builder setBaseUri(URI baseUri);

            /**
             * {@code /mcp/sse} is used by default.
             *
             * @param ssePath
             * @return self
             */
            Builder setSsePath(String ssePath);

            /**
             * @return self
             */
            Builder setExpectSseConnectionFailure();

            /**
             *
             * @return a new test client
             */
            McpSseTestClient build();
        }

    }

    /**
     * A test client that leverages the Streamable HTTP transport.
     */
    public interface McpStreamableTestClient
            extends McpTestClient<McpStreamableAssert, McpStreamableTestClient> {

        /**
         *
         * @return the MCP endpoint
         */
        URI mcpEndpoint();

        /**
         *
         * @return the current MCP session id
         */
        String mcpSessionId();

        /**
         * Terminates the current MCP session on the server.
         */
        void terminateSession();

        public interface Builder extends McpTestClient.Builder<Builder> {

            /**
             *
             * @param username
             * @param password
             * @return self
             */
            Builder setBasicAuth(String username, String password);

            /**
             * Set a function that is used to produce additional HTTP headers for a specific message. The message input may be
             * {@code null}.
             *
             * @param additionalHeaders
             * @return self
             */
            Builder setAdditionalHeaders(Function<JsonObject, MultiMap> additionalHeaders);

            /**
             *
             * @param baseUri
             * @return self
             */
            Builder setBaseUri(URI baseUri);

            /**
             * {@code /mcp} by default.
             *
             * @param mcpPath
             * @return self
             */
            Builder setMcpPath(String mcpPath);

            /**
             * If set to {@code true} then an subsidiary SSE stream is open to receive messages from the server.
             *
             * @return self
             */
            Builder setOpenSubsidiarySse(boolean value);

            /**
             * If set to {@code true} then the response to the initialize request must not return the status code {@code 200}.
             * <p>
             * Note that the client will not be connected after the invocation of {@link McpStreamableTestClient#connect()}.
             *
             * @return self
             */
            Builder setExpectConnectFailure();

            /**
             *
             * @return a new test client
             */
            McpStreamableTestClient build();
        }

    }

    /**
     * A test client that leverages the WebSocket transport.
     */
    public interface McpWebSocketTestClient
            extends McpTestClient<McpWebSocketAssert, McpWebSocketTestClient> {

        /**
         *
         * @return the WebSocket endpoint
         */
        URI endpointUri();

        public interface Builder extends McpTestClient.Builder<Builder> {

            /**
             *
             * @param username
             * @param password
             * @return self
             */
            Builder setBasicAuth(String username, String password);

            /**
             *
             * @param baseUri
             * @return self
             * @see McpAssured#baseUri
             */
            Builder setBaseUri(URI baseUri);

            /**
             * {@code /ws/mcp} is used by default.
             *
             * @param ssePath
             * @return self
             */
            Builder setEndpointPath(String path);

            /**
             *
             * @return a new test client
             */
            McpWebSocketTestClient build();
        }

    }

    /**
     * Represents a group of MCP requests and corresponding assert functions.
     * Each request is sent separately.
     */
    public interface McpAssert<ASSERT extends McpAssert<ASSERT>> {

        /**
         * Send a {@value McpAssured#PING} message and assert an empty pong result.
         *
         * @return self
         */
        default ASSERT pingPong() {
            return ping().withPongAssert().send();
        }

        /**
         * Build a {@value McpAssured#PING} message.
         *
         * @return a new builder
         */
        @CheckReturnValue
        PingMessage<ASSERT> ping();

        /**
         * Send a {@value McpAssured#TOOLS_LIST} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param assertFunction
         * @return self
         */
        @CheckReturnValue
        default ASSERT toolsList(Consumer<ToolsPage> assertFunction) {
            return toolsList().withAssert(assertFunction).send();
        }

        /**
         * Send a {@value McpAssured#TOOLS_LIST} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param assertFunction
         * @return a new builder
         */
        @CheckReturnValue
        ToolsListMessage<ASSERT> toolsList();

        /**
         * Send a {@value McpAssured#TOOLS_CALL} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param toolName
         * @param assertFunction
         * @return self
         */
        @CheckReturnValue
        default ASSERT toolsCall(String toolName, Consumer<ToolResponse> assertFunction) {
            return toolsCall(toolName)
                    .withAssert(assertFunction)
                    .send();
        }

        /**
         * Send a {@value McpAssured#TOOLS_CALL} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param toolName
         * @param args
         * @param assertFunction
         * @return self
         */
        @CheckReturnValue
        default ASSERT toolsCall(String toolName, Map<String, Object> args, Consumer<ToolResponse> assertFunction) {
            return toolsCall(toolName)
                    .withArguments(args)
                    .withAssert(assertFunction)
                    .send();
        }

        /**
         * Build a {@value McpAssured#TOOLS_CALL} message.
         * <p>
         * The message is not sent until the {@link ToolsCallMessage#send()} is called.
         *
         * @param toolName
         * @return a new builder
         */
        @CheckReturnValue
        ToolsCallMessage<ASSERT> toolsCall(String toolName);

        /**
         * Send a {@value McpAssured#PROMPTS_LIST} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param assertFunction
         * @return self
         */
        default ASSERT promptsList(Consumer<PromptsPage> assertFunction) {
            return promptsList().withAssert(assertFunction).send();
        }

        /**
         * Build a {@value McpAssured#PROMPTS_LIST} message.
         *
         * @param assertFunction
         * @return a new builder
         */
        PromptsListMessage<ASSERT> promptsList();

        /**
         * Send a {@value McpAssured#PROMPTS_GET} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param promptName
         * @param assertFunction
         * @return self
         */
        default ASSERT promptsGet(String promptName, Consumer<PromptResponse> assertFunction) {
            return promptsGet(promptName)
                    .withAssert(assertFunction)
                    .send();
        }

        /**
         * Send a {@value McpAssured#PROMPTS_GET} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param promptName
         * @param args
         * @param assertFunction
         * @return self
         */
        default ASSERT promptsGet(String promptName, Map<String, String> args, Consumer<PromptResponse> assertFunction) {
            return promptsGet(promptName)
                    .withArguments(args)
                    .withAssert(assertFunction)
                    .send();
        }

        /**
         * Send a {@value McpAssured#RESOURCES_READ} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param uri
         * @param assertFunction
         * @return self
         */
        default ASSERT resourcesRead(String uri, Consumer<ResourceResponse> assertFunction) {
            return resourcesRead(uri)
                    .withAssert(assertFunction)
                    .send();
        }

        /**
         * Build a {@value McpAssured#RESOURCES_READ} message.
         *
         * @param uri
         * @return a new builder
         */
        ResourcesReadMessage<ASSERT> resourcesRead(String uri);

        /**
         * Send a {@value McpAssured#RESOURCES_LIST} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param uri
         * @param assertFunction
         * @return self
         */
        default ASSERT resourcesList(Consumer<ResourcesPage> assertFunction) {
            return resourcesList()
                    .withAssert(assertFunction)
                    .send();
        }

        /**
         * Build a {@value McpAssured#RESOURCES_LIST} message.
         *
         * @return a new builder
         */
        ResourcesListMessage<ASSERT> resourcesList();

        /**
         * Send a {@value McpAssured#RESOURCES_TEMPLATES_LIST} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param uri
         * @param assertFunction
         * @return self
         */
        default ASSERT resourcesTemplatesList(Consumer<ResourcesTemplatesPage> assertFunction) {
            return resourcesTemplatesList()
                    .withAssert(assertFunction)
                    .send();
        }

        /**
         * Build a {@value McpAssured#RESOURCES_TEMPLATES_LIST} message.
         *
         * @return a new builder
         */
        ResourcesTemplatesListMessage<ASSERT> resourcesTemplatesList();

        /**
         * Build a {@value McpAssured#COMPLETION_COMPLETE} message.
         *
         * @param uriTemplate
         * @return a new builder
         */
        ResourceTemplateCompleteMessage<ASSERT> resourceTemplateComplete(String uriTemplate);

        /**
         * Send a {@value McpAssured#COMPLETION_COMPLETE} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param uriTemplate
         * @param argumentName
         * @param argumentValue
         * @param assertFunction
         * @return self
         */
        default ASSERT resourceTemplateComplete(String uriTemplate, String argumentName, String argumentValue,
                Consumer<CompletionResponse> assertFunction) {
            return resourceTemplateComplete(uriTemplate)
                    .withArgument(argumentName, argumentValue)
                    .withAssert(assertFunction)
                    .send();
        }

        /**
         * Build a {@value McpAssured#PROMPTS_GET} message.
         * <p>
         * The message is not sent until the {@link PromptsGetMessage#send()} is called.
         *
         * @param promptName
         * @return a new builder
         */
        PromptsGetMessage<ASSERT> promptsGet(String promptName);

        /**
         * Build a {@value McpAssured#COMPLETION_COMPLETE} message.
         *
         * @param promptName
         * @return a new builder
         */
        PromptsCompleteMessage<ASSERT> promptComplete(String promptName);

        /**
         * Send a {@value McpAssured#COMPLETION_COMPLETE} message to the server.
         * <p>
         * The assert function is not used until the {@link #thenAssertResults()} method is called.
         *
         * @param promptName
         * @param argumentName
         * @param argumentValue
         * @param assertFunction
         * @return self
         */
        default ASSERT promptComplete(String promptName, String argumentName, String argumentValue,
                Consumer<CompletionResponse> assertFunction) {
            return promptComplete(promptName)
                    .withArgument(argumentName, argumentValue)
                    .withAssert(assertFunction)
                    .send();
        }

        /**
         * Build a generic message.
         *
         * @param message
         * @return self
         */
        @CheckReturnValue
        GenericMessage<ASSERT> message(JsonObject message);

        /**
         * Blocks until all responses are available and all assert functions complete.
         *
         * @return the snapshot
         */
        Snapshot thenAssertResults();

        /**
         * A generic message.
         *
         * @param <ASSERT>
         */
        interface GenericMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @param errorAssertFunction
             * @return self
             */
            GenericMessage<ASSERT> withAssert(Consumer<JsonObject> assertFunction);

            /**
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @param errorAssertFunction
             * @return self
             */
            GenericMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message to the server.
             *
             * @return the assert group
             */
            ASSERT send();

        }

        /**
         * A {@code ping} message.
         *
         * @param <ASSERT>
         */
        interface PingMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             * Add an assert function that expects an empty result.
             * <p>
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @param errorAssertFunction
             * @return self
             */
            PingMessage<ASSERT> withPongAssert();

            /**
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @param errorAssertFunction
             * @return self
             */
            PingMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send a {@code ping} message to the server.
             *
             * @return the assert group
             */
            ASSERT send();
        }

        /**
         * A {@value McpAssured#TOOLS_LIST} message.
         *
         * @param <ASSERT>
         */
        interface ToolsListMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             *
             * @param cursor
             * @return self
             */
            ToolsListMessage<ASSERT> withCursor(String cursor);

            /**
             *
             * @param meta
             * @return self
             */
            ToolsListMessage<ASSERT> withMetadata(Map<String, Object> meta);

            /**
             *
             * @param assertFunction
             * @return self
             */
            ToolsListMessage<ASSERT> withAssert(Consumer<ToolsPage> assertFunction);

            /**
             *
             * @param errorAssertFunction
             * @return self
             */
            ToolsListMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message.
             * <p>
             * The assert functions are not used until the {@link #thenAssertResults()} method is called.
             *
             * @return the assert group
             */
            ASSERT send();
        }

        /**
         * A {@value McpAssured#TOOLS_CALL} message.
         *
         * @param <ASSERT>
         */
        interface ToolsCallMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             *
             * @param args
             * @return self
             */
            ToolsCallMessage<ASSERT> withArguments(Map<String, Object> args);

            /**
             *
             * @param meta
             * @return self
             */
            ToolsCallMessage<ASSERT> withMetadata(Map<String, Object> meta);

            /**
             *
             * @param assertFunction
             * @return self
             */
            ToolsCallMessage<ASSERT> withAssert(Consumer<ToolResponse> assertFunction);

            /**
             *
             * @param errorAssertFunction
             * @return self
             */
            ToolsCallMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message.
             * <p>
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @return the assert group
             */
            ASSERT send();

        }

        /**
         * A {@value McpAssured#PROMPTS_LIST} message.
         *
         * @param <ASSERT>
         */
        interface PromptsListMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             *
             * @param cursor
             * @return self
             */
            PromptsListMessage<ASSERT> withCursor(String cursor);

            /**
             *
             * @param meta
             * @return self
             */
            PromptsListMessage<ASSERT> withMetadata(Map<String, Object> meta);

            /**
             *
             * @param assertFunction
             * @return self
             */
            PromptsListMessage<ASSERT> withAssert(Consumer<PromptsPage> assertFunction);

            /**
             *
             * @param errorAssertFunction
             * @return self
             */
            PromptsListMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message.
             * <p>
             * The assert functions are not used until the {@link #thenAssertResults()} method is called.
             *
             * @return the assert group
             */
            ASSERT send();
        }

        /**
         * A {@value McpAssured#PROMPTS_GET} message.
         *
         * @param <ASSERT>
         */
        interface PromptsGetMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             * @param args
             * @return self
             */
            PromptsGetMessage<ASSERT> withArguments(Map<String, String> args);

            /**
             * @param meta
             * @return self
             */
            PromptsGetMessage<ASSERT> withMetadata(Map<String, Object> meta);

            /**
             * @param assertFunction
             * @return self
             */
            PromptsGetMessage<ASSERT> withAssert(Consumer<PromptResponse> assertFunction);

            /**
             * @param errorAssertFunction
             * @return self
             */
            PromptsGetMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message.
             * <p>
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @return the assert group
             */
            ASSERT send();

        }

        /**
         * A {@value McpAssured#COMPLETION_COMPLETE} message for prompts.
         *
         * @param <ASSERT>
         */
        interface PromptsCompleteMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             * @param name
             * @param value
             * @return self
             */
            PromptsCompleteMessage<ASSERT> withArgument(String name, String value);

            /**
             * @param arguments
             * @return self
             */
            PromptsCompleteMessage<ASSERT> withContext(Map<String, String> arguments);

            /**
             * @param assertFunction
             * @return self
             */
            PromptsCompleteMessage<ASSERT> withAssert(Consumer<CompletionResponse> assertFunction);

            /**
             * @param errorAssertFunction
             * @return self
             */
            PromptsCompleteMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message.
             * <p>
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @return the assert group
             */
            ASSERT send();

        }

        /**
         * A {@value McpAssured#COMPLETION_COMPLETE} message for resource templates.
         *
         * @param <ASSERT>
         */
        interface ResourceTemplateCompleteMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             * @param name
             * @param value
             * @return self
             */
            ResourceTemplateCompleteMessage<ASSERT> withArgument(String name, String value);

            /**
             * @param arguments
             * @return self
             */
            ResourceTemplateCompleteMessage<ASSERT> withContext(Map<String, String> arguments);

            /**
             * @param assertFunction
             * @return self
             */
            ResourceTemplateCompleteMessage<ASSERT> withAssert(Consumer<CompletionResponse> assertFunction);

            /**
             * @param errorAssertFunction
             * @return self
             */
            ResourceTemplateCompleteMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message.
             * <p>
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @return the assert group
             */
            ASSERT send();

        }

        /**
         * A {@value McpAssured#RESOURCES_LIST} message.
         *
         * @param <ASSERT>
         */
        interface ResourcesListMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             *
             * @param cursor
             * @return self
             */
            ResourcesListMessage<ASSERT> withCursor(String cursor);

            /**
             *
             * @param meta
             * @return self
             */
            ResourcesListMessage<ASSERT> withMetadata(Map<String, Object> meta);

            /**
             *
             * @param assertFunction
             * @return self
             */
            ResourcesListMessage<ASSERT> withAssert(Consumer<ResourcesPage> assertFunction);

            /**
             *
             * @param errorAssertFunction
             * @return self
             */
            ResourcesListMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message.
             * <p>
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @return the assert group
             */
            ASSERT send();

        }

        /**
         * A {@value McpAssured#RESOURCES_TEMPLATES_LIST} message.
         *
         * @param <ASSERT>
         */
        interface ResourcesTemplatesListMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             *
             * @param cursor
             * @return self
             */
            ResourcesTemplatesListMessage<ASSERT> withCursor(String cursor);

            /**
             *
             * @param meta
             * @return self
             */
            ResourcesTemplatesListMessage<ASSERT> withMetadata(Map<String, Object> meta);

            /**
             *
             * @param assertFunction
             * @return self
             */
            ResourcesTemplatesListMessage<ASSERT> withAssert(Consumer<ResourcesTemplatesPage> assertFunction);

            /**
             *
             * @param errorAssertFunction
             * @return self
             */
            ResourcesTemplatesListMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message.
             * <p>
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @return the assert group
             */
            ASSERT send();

        }

        /**
         * A {@value McpAssured#RESOURCES_READ} message.
         *
         * @param <ASSERT>
         */
        interface ResourcesReadMessage<ASSERT extends McpAssert<ASSERT>> {

            /**
             *
             * @param meta
             * @return self
             */
            ResourcesReadMessage<ASSERT> withMetadata(Map<String, Object> meta);

            /**
             *
             * @param assertFunction
             * @return self
             */
            ResourcesReadMessage<ASSERT> withAssert(Consumer<ResourceResponse> assertFunction);

            /**
             *
             * @param errorAssertFunction
             * @return self
             */
            ResourcesReadMessage<ASSERT> withErrorAssert(Consumer<McpError> errorAssertFunction);

            /**
             * Send the message.
             * <p>
             * The assert function is not used until the {@link #thenAssertResults()} method is called.
             *
             * @return the assert group
             */
            ASSERT send();

        }

    }

    public interface McpSseAssert extends McpAssert<McpSseAssert> {

        /**
         * The header is used for subsequent HTTP requests in this group.
         *
         * @param name
         * @param value
         * @return self
         */
        McpSseAssert addHeader(String name, String value);

        /**
         * The headers are used for subsequent HTTP requests in this group.
         *
         * @param additionalHeaders
         * @return self
         */
        McpSseAssert addHeaders(MultiMap additionalHeaders);

        /**
         * Use a custom HTTP response validator for subsequent HTTP requests in this group.
         * <p>
         * By default, an HTTP status code of value {@code 200} must be returned.
         *
         * @param validator
         * @return self
         */
        McpSseAssert validateHttpResponse(Consumer<HttpResponse> validator);

        /**
         * Use basic authentication for subsequent HTTP requests in this group.
         *
         * @param username
         * @param password
         * @return self
         */
        McpSseAssert basicAuth(String username, String password);

        /**
         * Do not use basic authentication for subsequent HTTP requests in this group.
         *
         * @return self
         */
        McpSseAssert noBasicAuth();

    }

    public interface McpWebSocketAssert extends McpAssert<McpWebSocketAssert> {

    }

    public interface McpStreamableAssert extends McpAssert<McpStreamableAssert> {

        /**
         * The headers are used for subsequent HTTP requests in this group.
         *
         * @param additionalHeaders
         * @return self
         */
        McpStreamableAssert addHeaders(MultiMap additionalHeaders);

        /**
         * Use basic authentication for subsequent HTTP requests in this group.
         *
         * @param username
         * @param password
         * @return self
         */
        McpStreamableAssert basicAuth(String username, String password);

        /**
         * Do not use basic authentication for subsequent HTTP requests in this group.
         *
         * @return self
         */
        McpStreamableAssert noBasicAuth();

    }

    public record InitResult(String protocolVersion, String serverName, String serverTitle, String serverVersion,
            List<ServerCapability> capabilities, String instructions) {

    }

    public record ServerCapability(String name, Map<String, Object> properties) {

    }

    public record ToolsPage(List<ToolInfo> tools, String nextCursor) {

        public int size() {
            return tools().size();
        }

        public ToolInfo findByName(String name) {
            return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
        }

    }

    public record PromptsPage(List<PromptInfo> prompts, String nextCursor) {

        public int size() {
            return prompts().size();
        }

        public PromptInfo findByName(String name) {
            return prompts.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
        }

    }

    public record ResourcesPage(List<ResourceInfo> resources, String nextCursor) {

        public int size() {
            return resources().size();
        }

        public ResourceInfo findByUri(String uri) {
            return resources.stream().filter(r -> r.uri().equals(uri)).findFirst().orElseThrow();
        }
    }

    public record ResourcesTemplatesPage(List<ResourceTemplateInfo> templates, String nextCursor) {

        public int size() {
            return templates().size();
        }

        public ResourceTemplateInfo findByUriTemplate(String uriTemplate) {
            return templates.stream().filter(t -> t.uriTemplate().equals(uriTemplate)).findFirst().orElseThrow();
        }
    }

    public record ToolInfo(String name, String title, String description, JsonObject inputSchema, JsonObject outputSchema,
            Optional<ToolAnnotations> annotations, JsonObject meta) {
    }

    public record PromptInfo(String name, String title, String description, List<PromptArgument> arguments, JsonObject meta) {
    }

    public record ResourceInfo(String uri, String mimeType, String name, String title, String description, Integer size,
            Content.Annotations annotations, JsonObject meta) {
    }

    public record ResourceTemplateInfo(String uriTemplate, String mimeType, String name, String title, String description,
            Content.Annotations annotations, JsonObject meta) {
    }

    public record PromptArgument(String name, String title, String description, boolean required) {
    }

    public record ToolAnnotations(String title, boolean readOnlyHint, boolean destructiveHint, boolean idempotentHint,
            boolean openWorldHint) {
    }

    /**
     * An immutable snapshot from the client.
     */
    public record Snapshot(List<JsonObject> requests, List<JsonObject> responses, List<JsonObject> notifications) {
    }

    public record HttpResponse(int statusCode, MultiMap headers, String body) {
    }

    public record McpError(int code, String message) {

    }

}
