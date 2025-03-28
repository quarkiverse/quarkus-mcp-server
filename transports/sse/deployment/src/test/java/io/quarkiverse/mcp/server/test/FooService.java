package io.quarkiverse.mcp.server.test;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class FooService {

    public String ping(String name, int repeat, Options options) {
        return options.enabled() ? "Hello %s!".formatted(name).repeat(repeat) : "Disabled";
    }

}
