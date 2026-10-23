package io.quarkiverse.mcp.server.schema.validator.it;

import io.quarkiverse.mcp.server.Tool;

public class SchemaValidatorServer {

    @Tool
    String bravo(int price) {
        return "foo" + price;
    }

}
