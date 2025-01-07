package io.quarkiverse.mcp.server;

import java.util.List;

public record InitializeRequest(Implementation implementation, String protocolVersion,
        List<ClientCapability> clientCapabilities) {

}
