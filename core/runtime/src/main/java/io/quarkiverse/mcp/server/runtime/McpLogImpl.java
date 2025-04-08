package io.quarkiverse.mcp.server.runtime;

import java.util.Objects;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkiverse.mcp.server.McpLog;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class McpLogImpl implements McpLog {

    private final String mcpLoggerName;

    private final Supplier<LogLevel> level;

    private final Logger log;

    private final Sender sender;

    McpLogImpl(Supplier<LogLevel> level, String loggerName, String mcpLoggerName, Sender sender) {
        this.mcpLoggerName = mcpLoggerName;
        this.level = level;
        this.log = Logger.getLogger(loggerName);
        this.sender = sender;
    }

    @Override
    public void send(LogLevel level, Object data) {
        if (isEnabled(Objects.requireNonNull(level))) {
            sender.send(Messages.newNotification(McpMessageHandler.NOTIFICATIONS_MESSAGE,
                    newLog(level, mcpLoggerName, encode(data))));
        }
    }

    @Override
    public void send(LogLevel level, String format, Object... params) {
        if (isEnabled(Objects.requireNonNull(level))) {
            sender.send(Messages.newNotification(McpMessageHandler.NOTIFICATIONS_MESSAGE,
                    newLog(level, mcpLoggerName, format.formatted(params))));
        }
    }

    @Override
    public LogLevel level() {
        return level.get();
    }

    @Override
    public void info(String format, Object... params) {
        log.infof(format, params);
        send(LogLevel.INFO, format, params);
    }

    @Override
    public void debug(String format, Object... params) {
        log.debugf(format, params);
        send(LogLevel.DEBUG, format, params);
    }

    @Override
    public void error(String format, Object... params) {
        log.errorf(format, params);
        send(LogLevel.ERROR, format, params);
    }

    @Override
    public void error(Throwable t, String format, Object... params) {
        log.infof(t, format, params);
        send(LogLevel.ERROR, format, params);
    }

    private boolean isEnabled(LogLevel level) {
        return level().ordinal() <= level.ordinal();
    }

    private Object encode(Object data) {
        if (data instanceof JsonObject || data instanceof JsonArray || data instanceof String) {
            return data;
        }
        return Json.encode(data);
    }

    private JsonObject newLog(LogLevel level, String loggerName, Object data) {
        return new JsonObject()
                .put("level", level.toString().toLowerCase())
                .put("logger", loggerName)
                .put("data", data);
    }

}