package io.quarkiverse.mcp.server.test.resources.templates;

import static io.quarkiverse.mcp.server.test.Checks.checkDuplicatedContext;
import static io.quarkiverse.mcp.server.test.Checks.checkExecutionModel;
import static io.quarkiverse.mcp.server.test.Checks.checkRequestContext;

import java.util.List;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.MetaField;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource.Annotations;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.vertx.core.json.JsonObject;

public class MyTemplates {

    @ResourceTemplate(uriTemplate = "file:///{path}", title = "Alpha...", annotations = @Annotations(audience = Role.USER, priority = 0.5))
    ResourceResponse alpha(String path) {
        String uri = "file:///" + path;
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return new ResourceResponse(List.of(TextResourceContents.create(uri, "foo:" + path)));
    }

    @ResourceTemplate(uriTemplate = "file:///bravo/{foo}/{bar}")
    TextResourceContents bravo(String foo, String bar, RequestUri uri) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return TextResourceContents.create(uri.value(), foo + ":" + bar);
    }

    @ResourceTemplate(uriTemplate = "file:///charlie/{foo}")
    TextResourceContents charlie(String foo) {
        return null;
    }

    @ResourceTemplate(uriTemplate = "file:///delta/{foo}")
    TextResourceContents delta(String foo) {
        throw new McpException("The resource could not be found", JsonRpcErrorCodes.INVALID_PARAMS);
    }

    @AlwaysError
    @ResourceTemplate(uriTemplate = "file:///echo/{foo}")
    TextResourceContents echo(String foo, RequestUri uri) {
        return TextResourceContents.create(uri.value(), foo);
    }

    @MetaField(name = "customField", value = "customValue")
    @MetaField(name = "version", type = MetaField.Type.INT, value = "2")
    @MetaField(name = "enabled", type = MetaField.Type.BOOLEAN, value = "true")
    @ResourceTemplate(uriTemplate = "file:///foxtrot/{path}", name = "foxtrot_custom_name", title = "Foxtrot Resource", description = "This is a detailed description for the foxtrot resource template", mimeType = "application/json")
    TextResourceContents foxtrot(String path, RequestUri uri) {
        return TextResourceContents.create(uri.value(), new JsonObject()
                .put("path", path)
                .encode());
    }

    @ResourceTemplate(uriTemplate = "file:///golf/{id}", title = "Golf Resource", description = "Resource with lastModified annotation", mimeType = "text/plain", annotations = @Annotations(audience = Role.ASSISTANT, lastModified = "2024-01-15T10:30:00Z", priority = 0.7))
    TextResourceContents golf(String id) {
        return TextResourceContents.create("file:///golf/" + id, "golf-" + id);
    }

}
