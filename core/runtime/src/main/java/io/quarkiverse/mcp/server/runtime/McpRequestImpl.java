package io.quarkiverse.mcp.server.runtime;

public class McpRequestImpl implements McpRequest {

    private final Object json;
    private final McpConnectionBase connection;
    private final Sender sender;
    private final SecuritySupport securitySupport;

    public McpRequestImpl(Object json, McpConnectionBase connection, Sender sender, SecuritySupport securitySupport) {
        this.json = json;
        this.connection = connection;
        this.sender = sender;
        this.securitySupport = securitySupport;
    }

    @Override
    public Object json() {
        return json;
    }

    @Override
    public McpConnectionBase connection() {
        return connection;
    }

    @Override
    public Sender sender() {
        return sender;
    }

    @Override
    public SecuritySupport securitySupport() {
        return securitySupport;
    }

}
