package io.quarkiverse.mcp.server.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.Feature;

public class FeatureArgumentProviderBuildItemTest {

    private static final DotName GREETER = DotName.createSimple("com.example.Greeter");

    @Test
    public void testDefaultsToAllFeatures() {
        FeatureArgumentProviderBuildItem item = new FeatureArgumentProviderBuildItem(GREETER, "com.example.GreeterProvider");
        assertEquals(EnumSet.allOf(Feature.class), item.getFeatures());
        for (Feature feature : Feature.values()) {
            assertTrue(item.appliesTo(feature));
        }
    }

    @Test
    public void testRestrictedFeatures() {
        FeatureArgumentProviderBuildItem item = new FeatureArgumentProviderBuildItem(GREETER, "com.example.GreeterProvider",
                EnumSet.of(Feature.TOOL));
        assertTrue(item.appliesTo(Feature.TOOL));
        assertEquals(false, item.appliesTo(Feature.PROMPT));
    }

    @Test
    public void testEmptyFeatures() {
        assertThrows(IllegalArgumentException.class,
                () -> new FeatureArgumentProviderBuildItem(GREETER, "com.example.GreeterProvider", Set.of()));
    }

    @Test
    public void testParameterTypeMustNotBeNull() {
        assertThrows(NullPointerException.class,
                () -> new FeatureArgumentProviderBuildItem(null, "com.example.GreeterProvider"));
    }

    @Test
    public void testProviderClassNameMustNotBeNull() {
        assertThrows(NullPointerException.class,
                () -> new FeatureArgumentProviderBuildItem(GREETER, null));
    }

    @Test
    public void testFeaturesMustNotBeNull() {
        assertThrows(NullPointerException.class,
                () -> new FeatureArgumentProviderBuildItem(GREETER, "com.example.GreeterProvider", null));
    }

    @Test
    public void testBuiltinTypeMustNotBeRegistered() {
        // McpConnection is a built-in injectable type
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FeatureArgumentProviderBuildItem(DotNames.MCP_CONNECTION, "com.example.GreeterProvider"));
        assertTrue(e.getMessage().contains(DotNames.MCP_CONNECTION.toString()));
    }

    @Test
    public void testBuiltinTypesRecognized() {
        assertTrue(FeatureArguments.isBuiltinType(DotNames.MCP_CONNECTION));
        assertEquals(false, FeatureArguments.isBuiltinType(GREETER));
    }
}
