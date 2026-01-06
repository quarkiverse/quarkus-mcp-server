package io.quarkiverse.mcp.server.test;

import static io.quarkiverse.mcp.server.test.McpTestClientBase.mustNotBeNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.quarkiverse.mcp.server.ClientCapability;
import io.quarkiverse.mcp.server.Icon;
import io.quarkiverse.mcp.server.runtime.McpMessageHandler;
import io.quarkiverse.mcp.server.test.McpAssured.McpTestClient.Builder;

abstract class McpTestClientBuilder<BUILDER extends Builder<BUILDER>> implements McpAssured.McpTestClient.Builder<BUILDER> {

    protected String name = "test-client";
    protected String version = "1.0";
    protected String protocolVersion = McpMessageHandler.SUPPORTED_PROTOCOL_VERSIONS.get(0);
    protected String title;
    protected String description;
    protected String websiteUrl;
    protected Set<ClientCapability> clientCapabilities = Set.of();
    protected Set<Icon> icons;
    protected boolean autoPong = true;

    @Override
    public BUILDER setName(String clientName) {
        if (clientName == null) {
            throw mustNotBeNull("clientName");
        }
        this.name = clientName;
        return self();
    }

    @Override
    public BUILDER setVersion(String clientVersion) {
        if (clientVersion == null) {
            throw mustNotBeNull("clientVersion");
        }
        this.version = clientVersion;
        return self();
    }

    @Override
    public BUILDER setProtocolVersion(String protocolVersion) {
        if (protocolVersion == null) {
            throw mustNotBeNull("protocolVersion");
        }
        this.protocolVersion = protocolVersion;
        return self();
    }

    @Override
    public BUILDER setTitle(String title) {
        if (title == null) {
            throw mustNotBeNull("title");
        }
        this.title = title;
        return self();
    }

    @Override
    public BUILDER setDescription(String description) {
        if (description == null) {
            throw mustNotBeNull("description");
        }
        this.description = description;
        return self();
    }

    @Override
    public BUILDER setIcons(Icon... icons) {
        this.icons = new HashSet<>(Arrays.asList(icons));
        return self();
    }

    @Override
    public BUILDER setWebsiteUrl(String websiteUrl) {
        if (websiteUrl == null) {
            throw mustNotBeNull("websiteUrl");
        }
        this.websiteUrl = websiteUrl;
        return self();
    }

    @Override
    public BUILDER setClientCapabilities(ClientCapability... capabilities) {
        this.clientCapabilities = new HashSet<>(Arrays.asList(capabilities));
        return self();
    }

    @Override
    public BUILDER setAutoPong(boolean val) {
        this.autoPong = val;
        return self();
    }

    @SuppressWarnings("unchecked")
    private BUILDER self() {
        return (BUILDER) this;
    }

}
