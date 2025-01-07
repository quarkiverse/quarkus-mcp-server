package io.quarkiverse.mcp.server.stdio.it;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class TestMain implements QuarkusApplication {

    @Override
    public int run(String... args) throws Exception {
        Quarkus.waitForExit();
        return 0;
    }

}
