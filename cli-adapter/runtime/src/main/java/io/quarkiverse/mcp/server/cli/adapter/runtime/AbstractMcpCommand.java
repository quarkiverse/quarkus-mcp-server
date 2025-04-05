package io.quarkiverse.mcp.server.cli.adapter.runtime;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.runtime.config.McpRuntimeConfig;
import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler;
import io.quarkus.runtime.Quarkus;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

public abstract class AbstractMcpCommand implements Callable<Integer> {

    @Option(names = { "--mcp" }, description = "Start an MCP server for the current CLI.")
    public boolean mcp;

    @Inject
    StdioMcpMessageHandler mcpMessageHandler;

    @Inject
    McpRuntimeConfig mcpRuntimeConfig;

    public abstract Integer doCall();

    @Override
    public Integer call() {
        if (mcp) {
            return startMcp();
        }
        return doCall();
    }

    public Integer startMcp() {
        PrintStream stdout = System.out;
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            mcpMessageHandler.initialize(stdout, mcpRuntimeConfig);
            Quarkus.waitForExit();
        } catch (Exception e) {
            return ExitCode.SOFTWARE;
        } finally {
            System.setOut(stdout);
        }
        return CommandLine.ExitCode.OK;
    }
}
