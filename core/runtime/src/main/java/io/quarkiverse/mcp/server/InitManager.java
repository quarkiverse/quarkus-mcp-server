package io.quarkiverse.mcp.server;

import io.quarkiverse.mcp.server.InitManager.InitInfo;

/**
 * This manager can be used to register a new {@link Init} callback programmatically.
 */
public interface InitManager extends FeatureManager<InitInfo> {

    /**
     *
     * @param name
     * @return the init callback with the given name, or {@code null}
     */
    InitInfo getInit(String name);

    /**
     *
     * @param name The name must be unique
     * @return a new definition builder
     * @see InitDefinition#register()
     */
    InitDefinition newInit(String name);

    /**
     * Removes an init callback previously added with {@link #newInit(String)}.
     *
     * @return the removed init callback or {@code null} if no such init callback existed
     */
    InitInfo removeInit(String name);

    /**
     * Init callback info.
     */
    interface InitInfo extends FeatureManager.FeatureInfo {

    }

    /**
     * {@link InitInfo} definition.
     * <p>
     * This construct is not thread-safe and should not be reused.
     */
    interface InitDefinition extends FeatureDefinition<InitInfo, InitArguments, Void, InitDefinition> {

    }

    record InitArguments(McpConnection connection, McpLog log) {
    }

}