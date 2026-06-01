package io.quarkiverse.mcp.server.runtime;

import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Singleton;

@Singleton
public class RequestIdGenerator {

    private final AtomicLong idGenerator = new AtomicLong();

    Long nextId() {
        return idGenerator.incrementAndGet();
    }

}
