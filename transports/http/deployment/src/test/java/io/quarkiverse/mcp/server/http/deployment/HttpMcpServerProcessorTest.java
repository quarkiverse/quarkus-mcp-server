package io.quarkiverse.mcp.server.http.deployment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.PrimitiveType;
import org.junit.jupiter.api.Test;

public class HttpMcpServerProcessorTest {

    @Test
    public void testIsAllowedHeaderTypePrimitives() {
        assertTrue(HttpMcpServerProcessor.isAllowedHeaderType(PrimitiveType.INT));
        assertTrue(HttpMcpServerProcessor.isAllowedHeaderType(PrimitiveType.LONG));
        assertTrue(HttpMcpServerProcessor.isAllowedHeaderType(PrimitiveType.BOOLEAN));
        assertFalse(HttpMcpServerProcessor.isAllowedHeaderType(PrimitiveType.DOUBLE));
        assertFalse(HttpMcpServerProcessor.isAllowedHeaderType(PrimitiveType.FLOAT));
        assertFalse(HttpMcpServerProcessor.isAllowedHeaderType(PrimitiveType.BYTE));
        assertFalse(HttpMcpServerProcessor.isAllowedHeaderType(PrimitiveType.SHORT));
        assertFalse(HttpMcpServerProcessor.isAllowedHeaderType(PrimitiveType.CHAR));
    }

    @Test
    public void testIsAllowedHeaderTypeBoxed() {
        assertTrue(HttpMcpServerProcessor.isAllowedHeaderType(ClassType.create(DotName.createSimple(String.class))));
        assertTrue(HttpMcpServerProcessor.isAllowedHeaderType(ClassType.create(DotName.createSimple(Integer.class))));
        assertTrue(HttpMcpServerProcessor.isAllowedHeaderType(ClassType.create(DotName.createSimple(Long.class))));
        assertTrue(HttpMcpServerProcessor.isAllowedHeaderType(ClassType.create(DotName.createSimple(Boolean.class))));
        assertFalse(HttpMcpServerProcessor.isAllowedHeaderType(ClassType.create(DotName.createSimple(Double.class))));
        assertFalse(
                HttpMcpServerProcessor.isAllowedHeaderType(ClassType.create(DotName.createSimple(java.util.List.class))));
        assertFalse(HttpMcpServerProcessor
                .isAllowedHeaderType(ClassType.create(DotName.createSimple(io.vertx.core.json.JsonObject.class))));
    }
}
