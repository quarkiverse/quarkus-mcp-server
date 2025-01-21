package io.quarkiverse.mcp.server.test.resources.templates;

import static io.quarkiverse.mcp.server.test.Checks.checkDuplicatedContext;
import static io.quarkiverse.mcp.server.test.Checks.checkExecutionModel;
import static io.quarkiverse.mcp.server.test.Checks.checkRequestContext;

import java.util.List;

import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;

public class MyTemplates {

    @ResourceTemplate(uriTemplate = "file:///{path}")
    ResourceResponse alpha(String path) {
        String uri = "file:///" + path;
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return new ResourceResponse(List.of(TextResourceContents.create(uri, "foo:" + path)));
    }

    @ResourceTemplate(uriTemplate = "file:///{foo}/{bar}")
    TextResourceContents bravo(String foo, String bar, RequestUri uri) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return TextResourceContents.create(uri.value(), foo + ":" + bar);
    }

}
