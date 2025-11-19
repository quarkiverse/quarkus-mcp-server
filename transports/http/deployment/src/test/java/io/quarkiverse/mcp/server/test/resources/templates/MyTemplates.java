package io.quarkiverse.mcp.server.test.resources.templates;

import static io.quarkiverse.mcp.server.test.Checks.checkDuplicatedContext;
import static io.quarkiverse.mcp.server.test.Checks.checkExecutionModel;
import static io.quarkiverse.mcp.server.test.Checks.checkRequestContext;

import java.util.List;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpException;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource.Annotations;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextResourceContents;

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

}
