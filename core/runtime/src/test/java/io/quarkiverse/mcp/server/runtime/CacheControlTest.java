package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.CacheControl;
import io.quarkiverse.mcp.server.CacheScope;

public class CacheControlTest {

    @Test
    public void testCacheScopeNotNull() {
        assertThrows(IllegalArgumentException.class, () -> new CacheControl(1000, null));
    }

    @Test
    public void testValidCacheControl() {
        CacheControl cc = new CacheControl(5000, CacheScope.PUBLIC);
        assertEquals(5000, cc.ttlMs());
        assertEquals(CacheScope.PUBLIC, cc.cacheScope());
    }

}
