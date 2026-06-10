package io.quarkiverse.mcp.server.test;

import java.net.http.HttpClient;

final class HttpClients {

    private static final HttpClient INSTANCE = HttpClient.newHttpClient();

    static HttpClient getDefault() {
        return INSTANCE;
    }

    private HttpClients() {
    }

}
