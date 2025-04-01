package io.quarkiverse.mcp.server;

import java.util.List;

/**
 * The initial request sent from a client.
 */
public record InitialRequest(Implementation implementation, String protocolVersion,
        List<ClientCapability> clientCapabilities) {

}
