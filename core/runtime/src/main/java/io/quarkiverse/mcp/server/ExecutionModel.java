package io.quarkiverse.mcp.server;

/**
 * An execution model of a feature method.
 *
 * @see Tool
 * @see Prompt
 * @see Resource
 */
public enum ExecutionModel {

    /**
     * Feature is considered blocking and should be executed on a worker thread.
     */
    WORKER_THREAD,

    /**
     * Feature is considered blocking and should be executed on a virtual thread.
     */
    VIRTUAL_THREAD,

    /**
     * Feature method is considered non-blocking and should be executed on an event loop thread.
     */
    EVENT_LOOP

}
