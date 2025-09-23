package io.quarkiverse.mcp.server.hibernate.validator.it;

import jakarta.validation.constraints.Min;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;

public class MyTools {

    @Tool
    // https://github.com/quarkusio/quarkus/issues/50225
    TextContent bravo(@Min(5) Integer price) {
        throw new ToolCallException("Business error");
    }

}
