package io.quarkiverse.mcp.server.test.stateless;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.runtime.SupportedProtocolVersions;

class InvalidProtocolVersionsTest {

    @Test
    void rejectsEmptySupportedVersions() {
        // supported-protocol-versions, when configured, must be a non-empty set
        assertThrows(IllegalArgumentException.class, () -> new SupportedProtocolVersions(List.of()));
    }
}
