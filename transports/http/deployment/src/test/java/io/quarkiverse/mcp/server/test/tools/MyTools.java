package io.quarkiverse.mcp.server.test.tools;

import static io.quarkiverse.mcp.server.test.Checks.checkDuplicatedContext;
import static io.quarkiverse.mcp.server.test.Checks.checkExecutionModel;
import static io.quarkiverse.mcp.server.test.Checks.checkRequestContext;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import dev.langchain4j.agent.tool.P;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.MetaField;
import io.quarkiverse.mcp.server.MetaField.Type;
import io.quarkiverse.mcp.server.MetaKey;
import io.quarkiverse.mcp.server.Role;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.FooService;
import io.quarkiverse.mcp.server.test.Options;
import io.smallrye.mutiny.Uni;

public class MyTools {

    @Inject
    FooService fooService;

    @MetaField(name = "priceLevel", value = "high")
    @MetaField(name = "price", type = Type.INT, value = "100")
    @MetaField(name = "active", type = Type.BOOLEAN, value = "true")
    @MetaField(name = "activeNumbers", type = Type.JSON, value = "[1, 2, 3]")
    @MetaField(name = "object", type = Type.JSON, value = """
            {\"foo\":true}
            """)
    @Tool
    ToolResponse alpha(@ToolArg(description = "Define the price...") Optional<Integer> price) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return new ToolResponse(false,
                List.of(new TextContent(
                        fooService.ping(price.get() + "", 1, new Options(true)),
                        Map.of(MetaKey.from("content/foo"), 10),
                        new Content.Annotations(Role.ASSISTANT,
                                ZonedDateTime.of(2025, 8, 26, 8, 40, 0, 0, ZoneOffset.UTC)
                                        .format(DateTimeFormatter.ISO_INSTANT),
                                0.5))),
                Map.of(MetaKey.of("alpha-foo"), true));
    }

    @Tool(title = "Uni Alpha!")
    Uni<ToolResponse> uni_alpha(@ToolArg(name = "uni_price") double price) {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item(ToolResponse.success(
                new TextContent(fooService.ping(price + "", 1, new Options(true)))));
    }

    @Tool
    TextContent bravo(int price) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return new TextContent(fooService.ping(price + "", 1, new Options(true)));
    }

    @Tool
    Uni<Content> uni_bravo(int price) {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item(new TextContent(fooService.ping(price + "", 1, new Options(true))));
    }

    @dev.langchain4j.agent.tool.Tool(name = "charlie", value = { "My description" })
    String langchain4jCharlie(@P(value = "day") DayOfWeek d, McpConnection connection) {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return DayOfWeek.MONDAY.equals(d) ? "charlie11" : "charlie1";
    }

    @Tool
    Uni<String> uni_charlie() {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item("charlie2");
    }

    @Tool
    List<String> list_charlie() {
        checkExecutionModel(true);
        checkDuplicatedContext();
        checkRequestContext();
        return List.of("charlie3");
    }

    @Tool
    Uni<List<String>> uni_list_charlie() {
        checkExecutionModel(false);
        checkDuplicatedContext();
        checkRequestContext();
        return Uni.createFrom().item(List.of("charlie4"));
    }

}
