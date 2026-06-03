package io.quarkiverse.mcp.server.cli.adapter.it;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@TopCommand
@Command(name = "code-service", description = "Calls the code service", mixinStandardHelpOptions = true)
public class CodeServiceCommand implements Callable<Integer> {

    @Inject
    CodeService codeService;

    @Option(names = { "-s", "--style" }, defaultValue = "plain", description = "The code style.")
    String codeStyle;

    @Parameters(defaultValue = "java", description = "The lanugage.")
    String language;

    @Override
    public Integer call() {
        if (codeStyle == null || codeStyle.isBlank()) {
            throw new IllegalStateException("Code style is required");
        }
        System.out.println(codeService.assist(language));
        return ExitCode.OK;
    }
}
