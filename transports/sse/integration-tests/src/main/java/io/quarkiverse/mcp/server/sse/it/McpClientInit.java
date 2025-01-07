package io.quarkiverse.mcp.server.sse.it;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.event.Observes;

import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.client.SseEvent;

import io.quarkus.logging.Log;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.vertx.ext.web.Router;

// A workaround to init the MCP client via SSE in the test
public class McpClientInit {

    void initRoute(@Observes Router router, @ConfigProperty(name = "quarkus.http.host") String host,
            @ConfigProperty(name = "quarkus.http.test-port") int port,
            @ConfigProperty(name = "quarkus.http.root-path") String root) {
        router.route("/test-init-mcp-client").blockingHandler(rc -> {
            try {
                URI baseUri = new URI("http://" + host + ":" + port + root);
                Log.infof("Test base URI: %s", baseUri);
                List<SseEvent<String>> sseMessages = new CopyOnWriteArrayList<>();
                McpClient mcpClient = QuarkusRestClientBuilder.newBuilder()
                        .baseUri(baseUri)
                        .build(McpClient.class);
                mcpClient.init().subscribe().with(s -> sseMessages.add(s), e -> {
                });
                Awaitility.await().until(() -> !sseMessages.isEmpty());
                URI endpoint = new URI(sseMessages.get(0).data());
                rc.end(endpoint.toString());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

}
