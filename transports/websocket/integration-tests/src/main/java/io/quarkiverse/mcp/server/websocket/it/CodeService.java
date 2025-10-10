package io.quarkiverse.mcp.server.websocket.it;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class CodeService {

    public String assist(String language) {
        return switch (language) {
            case "java" -> "System.out.println(\"Hello world!\");";
            default -> throw new IllegalArgumentException("Unexpected value: " + language);
        };
    }
}
