package org.acme;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GreetingTools {

    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    @Tool(description = "Greet With Hello a user by name")
    public String greetHello(@ToolArg(description = "The name", defaultValue = "Quarkus") String name) {
        return "Hello from the MCP server, " + name + "! - " + formatter.format(new Date());
    }

    @Tool(description = "Greet with Bye a user by name")
    public String greetBye(@ToolArg(description = "The name", defaultValue = "Quarkus") String name) {
        return "Bye from the MCP server, " + name + "! - " + formatter.format(new Date());
    }
}
