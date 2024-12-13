package io.quarkiverse.mcp.server.test.tools;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.Options;

public class MyTools {

    @Inject
    FooService fooService;

    @Tool
    ToolResponse alpha(@ToolArg(description = "Define the price...") int price) {
        return ToolResponse.success(
                new TextContent(fooService.ping(price + "", 1, new Options(true))));
    }

    @Tool
    ToolResponse uni_alpha(@ToolArg(name = "uni_price") double price) {
        return ToolResponse.success(
                new TextContent(fooService.ping(price + "", 1, new Options(true))));
    }
}
