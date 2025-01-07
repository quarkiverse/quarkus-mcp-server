package io.quarkiverse.mcp.server.test.resources;

import static io.quarkiverse.mcp.server.test.Checks.checkDuplicatedContext;
import static io.quarkiverse.mcp.server.test.Checks.checkExecutionModel;
import static io.quarkiverse.mcp.server.test.Checks.checkRequestContext;

import java.util.List;

import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.smallrye.mutiny.Uni;

public class MyResources {

    @Resource(uri = "file:///project/alpha")
    ResourceResponse alpha(String uri) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return new ResourceResponse(List.of(new TextResourceContents(uri, "1", null)));
    }

    @Resource(uri = "file:///project/uni_alpha")
    Uni<ResourceResponse> uni_alpha(String uri) {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item(new ResourceResponse(List.of(new TextResourceContents(uri, "2", null))));
    }

    @Resource(uri = "file:///project/bravo")
    TextResourceContents bravo(String uri) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return new TextResourceContents(uri, "3", null);
    }

    @Resource(uri = "file:///project/uni_bravo")
    Uni<TextResourceContents> uni_bravo() {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item(new TextResourceContents("file:///foo", "4", null));
    }
}
