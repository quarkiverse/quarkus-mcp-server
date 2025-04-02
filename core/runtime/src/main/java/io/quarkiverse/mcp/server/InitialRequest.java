package io.quarkiverse.mcp.server;

import java.util.List;

/**
 * The initial request sent from a client.
 *
 * @param implementation the client implementation information
 * @param protocolVersion the protocol version supported by the client
 * @param clientCapabilities the capabilities supported by the client
 */
public record InitialRequest(Implementation implementation, String protocolVersion,
        List<ClientCapability> clientCapabilities) {

    /**
     * @return {@code true} if the client supports the {@link ClientCapability#SAMPLING} capability
     */
    public boolean supportsSampling() {
        return supportsCapability(ClientCapability.SAMPLING);
    }

    /**
     * @return {@code true} if the client supports the {@link ClientCapability#ROOTS} capability
     */
    public boolean supportsRoots() {
        return supportsCapability(ClientCapability.ROOTS);
    }

    /**
     * @return {@code true} if the client supports the specified capability
     */
    public boolean supportsCapability(String name) {
        for (ClientCapability capability : clientCapabilities) {
            if (capability.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

}
