package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * A {@link CompletePrompt} or {@link CompleteResourceTemplate} method may accept this class as a parameter. It will be
 * automatically injected before the method is invoked.
 *
 * @see CompletePrompt
 * @see CompleteResourceTemplate
 */
public interface CompleteContext {

    /**
     * The previous completions can provide context for subsequent requests.
     *
     * @return the previous completions
     */
    Map<String, String> arguments();

}
