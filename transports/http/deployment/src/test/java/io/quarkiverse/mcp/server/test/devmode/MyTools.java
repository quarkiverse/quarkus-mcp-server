package io.quarkiverse.mcp.server.test.devmode;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;

public class MyTools {

    @Tool
    String bravo(int price) {
        throw new ToolCallException("Business error");
    }

}