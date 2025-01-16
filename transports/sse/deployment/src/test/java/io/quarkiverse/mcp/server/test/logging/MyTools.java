package io.quarkiverse.mcp.server.test.logging;

import java.time.DayOfWeek;

import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.McpLog.LogLevel;
import io.quarkiverse.mcp.server.Tool;

public class MyTools {

    @Tool
    String charlie(DayOfWeek day, McpLog log) {
        if (day == DayOfWeek.FRIDAY || day == DayOfWeek.MONDAY) {
            log.info("Charlie does not work on %s", day);
        } else if (day == DayOfWeek.WEDNESDAY) {
            log.send(LogLevel.CRITICAL, "Wednesday is critical!");
        }
        return day.toString().toLowerCase() + ":" + log.level();
    }
}
