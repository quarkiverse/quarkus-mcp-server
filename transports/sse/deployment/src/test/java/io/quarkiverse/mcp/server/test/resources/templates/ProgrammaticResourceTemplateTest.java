package io.quarkiverse.mcp.server.test.resources.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Content.Annotations;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.runtime.JsonRPC;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ResourceTemplateInfo;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;

public class ProgrammaticResourceTemplateTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTemplates.class));

    @Inject
    MyTemplates myTemplates;

    @Test
    public void testResources() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();

        client.when()
                .resourcesTemplatesList(p -> assertEquals(0, p.size()))
                .resourcesRead("file:///alpha/nok")
                .withErrorAssert(e -> {
                    assertEquals(JsonRPC.RESOURCE_NOT_FOUND, e.code());
                    assertEquals("Invalid resource uri: file:///alpha/nok", e.message());
                })
                .send()
                .thenAssertResults();

        myTemplates.register("alpha");
        assertThrows(IllegalArgumentException.class, () -> myTemplates.register("alpha"));
        assertThrows(NullPointerException.class, () -> myTemplates.register(null));

        client.when()
                .resourcesTemplatesList(p -> {
                    assertEquals(1, p.size());
                    ResourceTemplateInfo alpha = p.findByUriTemplate("file:///alpha/{foo}");
                    assertNotNull(alpha.annotations());
                    assertEquals(Role.ASSISTANT, alpha.annotations().audience());
                    assertEquals(0.9, alpha.annotations().priority());
                })
                .resourcesRead("file:///alpha/ok", r -> assertEquals("ok", r.contents().get(0).asText().text()))
                .thenAssertResults();

        myTemplates.register("bravo");

        client.when()
                .resourcesTemplatesList(p -> assertEquals(2, p.size()))
                .resourcesRead("file:///bravo/bim", r -> assertEquals("bim", r.contents().get(0).asText().text()))
                .thenAssertResults();

        myTemplates.remove("alpha");

        client.when()
                .resourcesTemplatesList(p -> assertEquals(1, p.size()))
                .resourcesRead("file:///alpha/nok")
                .withErrorAssert(e -> {
                    assertEquals(JsonRPC.RESOURCE_NOT_FOUND, e.code());
                    assertEquals("Invalid resource uri: file:///alpha/nok", e.message());
                })
                .send()
                .resourcesRead("file:///bravo/bim", r -> assertEquals("bim", r.contents().get(0).asText().text()))
                .thenAssertResults();
    }

    @Singleton
    public static class MyTemplates {

        @Inject
        ResourceTemplateManager manager;

        void register(String name) {
            manager.newResourceTemplate(name)
                    .setUriTemplate("file:///" + name + "/{foo}")
                    .setDescription(name + " description!")
                    .setAnnotations(new Annotations(Role.ASSISTANT, null, .9))
                    .setHandler(
                            args -> new ResourceResponse(
                                    List.of(TextResourceContents.create(args.requestUri().value(), args.args().get("foo")))))
                    .register();
        }

        ResourceTemplateManager.ResourceTemplateInfo remove(String name) {
            return manager.removeResourceTemplate(name);
        }

    }

}
