//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus.platform:quarkus-bom:3.18.3@pom
//DEPS io.quarkus:quarkus-picocli
//Q:CONFIG quarkus.log.console.stderr=true
package io.quarkiverse.mcp.server.proxy;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;

import io.quarkus.runtime.Quarkus;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Starts a proxy server between an MCP client using the {@code stdio} transport and an MCP server using the {@code HTTP/SSE}
 * transport. Sends all MCP messages to the target MCP server and writes the MCP responses to the standard output respectively.
 */
@Command(name = "stdio-sse-proxy", mixinStandardHelpOptions = true)
public class StdioSseProxy implements Runnable {

    private static final Logger LOG = Logger.getLogger(StdioSseProxy.class);

    @Option(names = { "-e", "--endpoint" }, required = true, description = "The URI of the target SSE endpoint")
    URI sseEndpoint;

    @Option(names = { "-t",
            "--timeout" }, defaultValue = "10", description = "The timeout in seconds; used when connecting to the SSE endpoint and to obtain the message endpoint")
    int timeout;

    @Option(names = { "-s",
            "--sleep" }, defaultValue = "60", description = "The sleep time in milliseconds; used when processing the stdin queue")
    int sleep;

    @Option(names = {
            "--reconnect" }, negatable = true, defaultValue = "true", description = "If set to true then the proxy attempts to reconnect if a message endpoint returns http status 400")
    boolean reconnect;

    @Override
    public void run() {
        LOG.infof("Stdio -> SSE [sse: %s, timeout: %s, reconnect: %s, sleep: %s]", sseEndpoint, timeout, reconnect, sleep);

        InputStream in = System.in;
        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        Queue<String> inQueue = new ConcurrentLinkedQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .build();

        AtomicReference<URI> messageEndpoint = new AtomicReference<>();
        EndpointPhaser endpointReceived = new EndpointPhaser();

        SseClient sseClient = new SseClient(sseEndpoint) {
            @Override
            protected void process(SseEvent event) {
                if ("endpoint".equals(event.name())) {
                    String endpoint = event.data().strip();
                    LOG.infof("Message endpoint received: %s", endpoint);
                    messageEndpoint.set(sseEndpoint.resolve(endpoint));
                    endpointReceived.countDown();
                } else {
                    out.println(event.data());
                }
            }
        };
        sseClient.connect(client);

        executor.submit(new Runnable() {
            @Override
            public void run() {

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            LOG.debug("EOF received, exiting");
                            Quarkus.asyncExit(0);
                            return;
                        }
                        if (line.isBlank()) {
                            continue;
                        }
                        LOG.debugf("Line added to queue:\n%s", line);
                        inQueue.offer(line);
                    }
                } catch (IOException e) {
                    LOG.errorf(e, "Error reading stdio");
                }
            }
        });

        executor.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    while (true) {
                        try {
                            endpointReceived.await(timeout, TimeUnit.SECONDS);
                        } catch (TimeoutException e) {
                            LOG.errorf(e, "Message endpoint not received in %s seconds", timeout);
                        }
                        String line = inQueue.poll();
                        if (line != null && !line.isBlank()) {
                            try {
                                sendData(sseClient, endpointReceived, client, messageEndpoint, line, false);
                            } catch (IOException e) {
                                LOG.errorf(e, "Unable to send POST request to %s", messageEndpoint.get());
                            }
                        }
                        TimeUnit.MILLISECONDS.sleep(sleep);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Quarkus.waitForExit();
    }

    private void sendData(SseClient sseClient, EndpointPhaser endpointReceived, HttpClient httpClient,
            AtomicReference<URI> messageEndpoint, String data,
            boolean reconnectAttempt)
            throws IOException, InterruptedException {
        LOG.debugf("%s data to SSE:\n%s", reconnectAttempt ? "Resending" : "Sending", data);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(messageEndpoint.get())
                .version(Version.HTTP_1_1)
                .POST(BodyPublishers.ofString(data))
                .build();
        HttpResponse<?> response = httpClient.send(request, BodyHandlers.discarding());
        if (response.statusCode() == 400 && reconnect && !reconnectAttempt) {
            // The connection was very likely closed
            // Let's reconnect the SSE client
            LOG.infof("Message endpoint %s not found - reconnecting SSE client..", messageEndpoint.get());
            endpointReceived.reset();
            sseClient.connect(httpClient);
            try {
                endpointReceived.await(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                LOG.errorf(e, "Message endpoint not received in %s seconds", timeout);
                return;
            }
            sendData(sseClient, endpointReceived, httpClient, messageEndpoint, data, true);
        } else if (response.statusCode() != 200) {
            LOG.errorf("Received erroneous status code: %s", response.statusCode());
        }
    }

    public abstract class SseClient {

        private static final Logger LOG = Logger.getLogger(SseClient.class);

        private final URI sseUri;

        public SseClient(URI uri) {
            this.sseUri = uri;
        }

        protected abstract void process(SseEvent event);

        public void connect(HttpClient client) {
            if (client == null) {
                client = HttpClient.newHttpClient();
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(sseUri)
                    .version(Version.HTTP_1_1)
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();

            client.sendAsync(request, BodyHandlers.fromLineSubscriber(new SseEventSubscriber()))
                    .exceptionally(e -> {
                        if (e instanceof CompletionException) {
                            e = e.getCause();
                        }
                        if (e instanceof ConnectException ce) {
                            LOG.errorf(ce.getCause(), "Connection failed: %s", sseEndpoint);
                            Quarkus.asyncExit(0);
                        } else {
                            Throwable root = getRootCause(e);
                            if (!(root instanceof EOFException)) {
                                LOG.error(e);
                            }
                        }
                        return null;
                    });
        }

        class SseEventSubscriber implements Flow.Subscriber<String> {

            private Flow.Subscription subscription;

            private String event = "message";
            private StringBuilder dataBuffer = new StringBuilder();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
                LOG.infof("Connected to SSE stream: %s", sseUri);
            }

            @Override
            public void onNext(String line) {
                if (line.startsWith(":")) {
                    // Skip comments
                } else if (line.isBlank()) {
                    // Flush
                    process(new SseEvent(event, dataBuffer.toString()));
                    event = "message";
                    dataBuffer = new StringBuilder();
                } else if (line.contains(":")) {
                    LOG.debugf("Received next line:\n%s", line);
                    int colon = line.indexOf(":");
                    String field = line.substring(0, colon).strip();
                    String value = line.substring(colon + 1).strip();
                    handleField(field, value);
                } else {
                    // The whole line is the field name
                    handleField(line, "");
                }
                subscription.request(1);
            }

            @Override
            public void onError(Throwable t) {
                Throwable root = getRootCause(t);
                if (!(root instanceof EOFException)) {
                    LOG.errorf(t, "Error in SSE stream");
                }
            }

            @Override
            public void onComplete() {
                LOG.info("SSE connection closed");
            }

            private void handleField(String field, String value) {
                switch (field) {
                    case "event" -> event = value;
                    case "data" -> dataBuffer.append(value).append("\n");
                }
            }
        }

        public record SseEvent(String name, String data) {
        }

        private static Throwable getRootCause(Throwable exception) {
            final List<Throwable> chain = new ArrayList<>();
            Throwable curr = exception;
            while (curr != null && !chain.contains(curr)) {
                chain.add(curr);
                curr = curr.getCause();
            }
            return chain.isEmpty() ? null : chain.get(chain.size() - 1);
        }
    }

    class EndpointPhaser {

        private final Phaser phaser = new Phaser(1);

        private final AtomicInteger phase = new AtomicInteger(0);

        public void countDown() {
            phaser.arrive();
        }

        public void await(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
            phaser.awaitAdvanceInterruptibly(phase.get(), timeout, unit);
        }

        public void reset() {
            phase.incrementAndGet();
        }
    }

}
