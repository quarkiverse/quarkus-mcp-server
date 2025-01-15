package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.runtime.ResourceTemplateManager.VariableMatcher;

public class ResourceTemplateManagerTest {

    @Test
    public void testCreateMatcherFromUriTemplate() {
        assertVariableMatcher("file:///{foo}", "file:///bar", Map.of("foo", "bar"));
        assertVariableMatcher("file:///{foo}/{bar}/baz", "file:///alpha/bravo/baz", Map.of("foo", "alpha", "bar", "bravo"));

    }

    private void assertVariableMatcher(String uriTemplate, String uri, Map<String, Object> expectedVars) {
        VariableMatcher matcher = ResourceTemplateManager.createMatcherFromUriTemplate(uriTemplate);
        assertTrue(expectedVars.keySet().containsAll(matcher.variables()));
        assertTrue(matcher.pattern().matcher(uri).matches());
        Map<String, Object> matchedVars = matcher.matchVariables(uri);
        assertEquals(expectedVars, matchedVars);
    }

}
