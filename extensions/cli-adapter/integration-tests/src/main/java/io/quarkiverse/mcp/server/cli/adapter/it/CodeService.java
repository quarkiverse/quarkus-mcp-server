package io.quarkiverse.mcp.server.cli.adapter.it;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CodeService {

    public String assist(String language) {
        return switch (language) {
            case "java" -> "System.out.println(\"Hello world!\");";
            default -> throw new IllegalArgumentException("Unexpected value: " + language);
        };
    }
}
