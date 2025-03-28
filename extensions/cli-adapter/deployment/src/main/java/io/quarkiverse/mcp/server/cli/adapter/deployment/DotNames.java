package io.quarkiverse.mcp.server.cli.adapter.deployment;

import java.util.Optional;

import jakarta.inject.Qualifier;

import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import picocli.CommandLine;
import picocli.CommandLine.Command;

final class DotNames {
    static final DotName TOOL = DotName.createSimple(Tool.class);
    static final DotName TOOL_ARG = DotName.createSimple(ToolArg.class);
    static final DotName COMMAND = DotName.createSimple(Command.class);
    static final DotName QUALIFIER = DotName.createSimple(Qualifier.class);

    static final DotName COMMANDLINE = DotName.createSimple(CommandLine.class);
    static final DotName OPTION = DotName.createSimple(CommandLine.Option.class);
    static final DotName PARAMETERS = DotName.createSimple(CommandLine.Parameters.class);

    static final DotName OPTIONAL = DotName.createSimple(Optional.class);
    static final DotName STRING = DotName.createSimple(String.class);
}
