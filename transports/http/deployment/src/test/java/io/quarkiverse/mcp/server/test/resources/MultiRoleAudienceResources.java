package io.quarkiverse.mcp.server.test.resources;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.Resource.Annotations;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextResourceContents;

public class MultiRoleAudienceResources {

    @Resource(uri = "file:///multi/alpha", annotations = @Annotations(audience = { Role.USER,
            Role.ASSISTANT }, priority = 0.6))
    TextResourceContents alpha(RequestUri uri) {
        return TextResourceContents.create(uri.value(), "alpha");
    }

    @ResourceTemplate(uriTemplate = "file:///multi/template/{id}", annotations = @Annotations(audience = { Role.ASSISTANT,
            Role.USER }, priority = 0.7))
    TextResourceContents template(String id) {
        return TextResourceContents.create("file:///multi/template/" + id, "template-" + id);
    }

    @Singleton
    public static class ProgrammaticRegistrar {

        @Inject
        ResourceManager resourceManager;

        @Inject
        ResourceTemplateManager resourceTemplateManager;

        void registerResource() {
            resourceManager.newResource("programmatic_multi")
                    .setUri("file:///multi/programmatic")
                    .setDescription("Programmatic multi-role resource")
                    .setAnnotations(new Content.Annotations(List.of(Role.ASSISTANT, Role.USER), null, 0.8))
                    .setHandler(args -> new ResourceResponse(
                            List.of(TextResourceContents.create(args.requestUri().value(), "programmatic"))))
                    .register();
        }

        void registerResourceTemplate() {
            resourceTemplateManager.newResourceTemplate("programmatic_template_multi")
                    .setUriTemplate("file:///multi/programmatic_template/{name}")
                    .setDescription("Programmatic multi-role resource template")
                    .setAnnotations(new Content.Annotations(List.of(Role.USER, Role.ASSISTANT), null, 0.9))
                    .setHandler(args -> new ResourceResponse(
                            List.of(TextResourceContents.create(args.requestUri().value(), args.args().get("name")))))
                    .register();
        }
    }
}
