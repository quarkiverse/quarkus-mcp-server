package io.quarkiverse.mcp.server.test.resources;

import static io.quarkiverse.mcp.server.test.Checks.checkDuplicatedContext;
import static io.quarkiverse.mcp.server.test.Checks.checkExecutionModel;
import static io.quarkiverse.mcp.server.test.Checks.checkRequestContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.Resource.Annotations;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.smallrye.mutiny.Uni;

public class MyResources {

    @Resource(uri = "file:///project/alpha", title = "Alpha...", size = 15, annotations = @Annotations(audience = Role.USER, priority = 0.5))
    ResourceResponse alpha(RequestUri uri) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return new ResourceResponse(List.of(new TextResourceContents(uri.value(), "1", null)));
    }

    @Resource(uri = "file:///project/uni_alpha")
    Uni<ResourceResponse> uni_alpha(RequestUri uri) {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item(new ResourceResponse(List.of(new TextResourceContents(uri.value(), "2", null))));
    }

    @Resource(uri = "file:///project/bravo")
    TextResourceContents bravo(RequestUri uri) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return new TextResourceContents(uri.value(), "3", null);
    }

    @Resource(uri = "file:///project/uni_bravo")
    Uni<TextResourceContents> uni_bravo() {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item(new TextResourceContents("file:///foo", "4", null));
    }

    @Resource(uri = "file:///project/cs_alpha")
    CompletionStage<TextResourceContents> cs_alpha(RequestUri uri) {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return CompletableFuture.completedFuture(new TextResourceContents(uri.value(), "5", null));
    }
}
