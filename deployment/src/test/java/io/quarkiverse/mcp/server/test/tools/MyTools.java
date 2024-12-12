package io.quarkiverse.mcp.server.test.tools;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.Options;

public class MyTools {

    @Inject
    FooService fooService;

    @Tool
    ToolResponse alpha(int price) {
        return ToolResponse.success(
                new TextContent(fooService.ping(price + "", 1, new Options(true))));
    }
}
