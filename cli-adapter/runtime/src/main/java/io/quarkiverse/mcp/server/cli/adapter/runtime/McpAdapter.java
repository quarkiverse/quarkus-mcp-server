package io.quarkiverse.mcp.server.cli.adapter.runtime;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkiverse.mcp.server.stdio.runtime.StdioMcpMessageHandler;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.Quarkus;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

public class McpAdapter {

    public static Integer startMcp() {
        PrintStream stdout = System.out;
        try {
            StdioMcpMessageHandler mcpMessageHandler = Arc.container().instance(StdioMcpMessageHandler.class).get();
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            mcpMessageHandler.initialize(stdout);
            Quarkus.waitForExit();
        } catch (Exception e) {
            return ExitCode.SOFTWARE;
        } finally {
            System.setOut(stdout);
        }
        return CommandLine.ExitCode.OK;
    }

    public static String callCommand(Object command, Map<?, ?> opts, List<?> args) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream((OutputStream) baos)) {
            System.setOut(out);
            List<String> executeArguments = new ArrayList<>();
            for (Map.Entry<?, ?> entry : opts.entrySet()) {
                String stringKey = toString(entry.getKey());
                String stringValue = toString(entry.getValue());
                if (stringValue == null || stringValue.isBlank()) {
                    continue;
                }
                executeArguments.add(stringKey);
                executeArguments.add(stringValue);
            }
            for (Object arg : args) {
                String stringArg = toString(arg);
                if (stringArg != null && !stringArg.isBlank()) {
                    executeArguments.add(stringArg);
                }
            }
            int result = new CommandLine(command).execute(executeArguments.toArray(String[]::new));
            out.flush();
            if (result != CommandLine.ExitCode.OK) {
                return new String(baos.toByteArray()) +
                        "\n" +
                        "Command failed with exit code: " + result;
            }
            return new String(baos.toByteArray());
        } catch (Exception e) {
            return e.getMessage();
        } finally {
            System.setOut(originalOut);
        }
    }

    public static String toString(Object obj) {
        if (obj == null) {
            return null;
        } else if (obj instanceof Optional) {
            return ((Optional<?>) obj).map(String::valueOf).orElse("");
        }
        return String.valueOf(obj);
    }
}
