package io.quarkiverse.mcp.server;

import org.jboss.logging.Logger;

/**
 * Send log message notifications to a connected MCP client.
 * <p>
 * The MCP logger name is derived from the method. For example, if there is a method {@code myTool()} annotated with
 * {@code @Tool} then the logger name will be {@code tool:myTool}.
 * <p>
 * The {@code format} and {@code params} arguments of the logging methods use {@link java.util.Formatter} syntax
 * (for example {@code %s}, {@code %d}, {@code %%}). SLF4J-style <code>{}</code> placeholders are <em>not</em>
 * supported and will appear verbatim in the output. For example:
 *
 * <pre>{@code
 * log.info("Processed %d of %d items", processed, total);
 * }</pre>
 */
public interface McpLog {

    /**
     * Returns the current log level, i.e. the threshold used to decide whether a log message notification is sent to the
     * client.
     * <p>
     * For a stateless request (protocol version {@code 2026-07-28} and later) the level is opt-in per-request via the
     * {@code io.modelcontextprotocol/logLevel} {@code _meta} field. If the client did not include this field then this
     * method returns {@code null} and no log message notification is ever sent to the client; the logging methods, such
     * as {@link #info(String, Object...)}, still log the message server-side but do not notify the client.
     *
     * @return the current log level, or {@code null} if the client did not opt in to log message notifications
     */
    LogLevel level();

    /**
     * Sends a log message notification to the client if the specified level is higher or equal to the current level.
     * <p>
     * If {@link #level()} returns {@code null}, i.e. the client did not opt in to log message notifications, then no
     * notification is sent.
     *
     * @param level
     * @param data
     */
    void send(LogLevel level, Object data);

    /**
     * Sends a log message notification to the client if the specified level is higher or equal to the current level.
     * <p>
     * If {@link #level()} returns {@code null}, i.e. the client did not opt in to log message notifications, then no
     * notification is sent.
     *
     * @param level
     * @param format a format string in {@link java.util.Formatter} syntax
     * @param params arguments referenced by the format specifiers
     */
    void send(LogLevel level, String format, Object... params);

    /**
     * Logs a message and sends a {@link LogLevel#DEBUG} log message notification to the client.
     *
     * @param format a format string in {@link java.util.Formatter} syntax
     * @param params arguments referenced by the format specifiers
     * @see Logger#debugf(String, Object...)
     */
    void debug(String format, Object... params);

    /**
     * Logs a message and sends a {@link LogLevel#INFO} log message notification to the client.
     *
     * @param format a format string in {@link java.util.Formatter} syntax
     * @param params arguments referenced by the format specifiers
     * @see Logger#infof(String, Object...)
     */
    void info(String format, Object... params);

    /**
     * Logs a message and sends a {@link LogLevel#ERROR} log message notification to the client.
     *
     * @param format a format string in {@link java.util.Formatter} syntax
     * @param params arguments referenced by the format specifiers
     * @see Logger#errorf(String, Object...)
     */
    void error(String format, Object... params);

    /**
     * Logs a message and sends a {@link LogLevel#ERROR} log message notification to the client.
     *
     * @param t
     * @param format a format string in {@link java.util.Formatter} syntax
     * @param params arguments referenced by the format specifiers
     * @see Logger#errorf(Throwable, String, Object...)
     */
    void error(Throwable t, String format, Object... params);

    public enum LogLevel {
        DEBUG,
        INFO,
        NOTICE,
        WARNING,
        ERROR,
        CRITICAL,
        ALERT,
        EMERGENCY;

        /**
         * @param threshold the minimum level
         * @return {@code true} if this level is at least the given threshold
         */
        public boolean isAtLeast(LogLevel threshold) {
            return this.ordinal() >= threshold.ordinal();
        }

        public static LogLevel from(String val) {
            if (val == null || val.isBlank()) {
                return null;
            }
            try {
                return valueOf(val.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }

    }

}
