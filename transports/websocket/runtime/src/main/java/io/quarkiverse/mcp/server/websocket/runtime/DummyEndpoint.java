package io.quarkiverse.mcp.server.websocket.runtime;

import jakarta.enterprise.inject.Vetoed;

import io.quarkus.websockets.next.WebSocket;

@Vetoed
@WebSocket(path = "dummy")
class DummyEndpoint {

}
