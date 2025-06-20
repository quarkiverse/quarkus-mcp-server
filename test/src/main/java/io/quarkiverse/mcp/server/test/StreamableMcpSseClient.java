package io.quarkiverse.mcp.server.test;

import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.util.Map;

public class StreamableMcpSseClient extends McpSseClient {

    private final Map<String, String> additionalHeaders;

    private final BodyPublisher bodyPublisher;

    public StreamableMcpSseClient(URI uri, BodyPublisher bodyPublisher, Map<String, String> additionalHeaders) {
        super(uri);
        this.bodyPublisher = bodyPublisher;
        this.additionalHeaders = additionalHeaders;
    }

    @Override
    protected HttpRequest buildConnectRequest(Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(connectUri)
                .version(Version.HTTP_1_1)
                .header("Accept", "text/event-stream")
                .POST(bodyPublisher);
        headers.forEach(builder::header);
        additionalHeaders.forEach(builder::header);
        return builder.build();
    }

}
