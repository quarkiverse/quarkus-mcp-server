package io.quarkiverse.mcp.server.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolResponse;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

class DotNames {

    static final DotName PROMPT = DotName.createSimple(Prompt.class);
    static final DotName PROMPT_ARG = DotName.createSimple(PromptArg.class);
    static final DotName PROMPT_RESPONSE = DotName.createSimple(PromptResponse.class);
    static final DotName PROMPT_MESSAGE = DotName.createSimple(PromptMessage.class);
    static final DotName TOOL = DotName.createSimple(Tool.class);
    static final DotName TOOL_RESPONSE = DotName.createSimple(ToolResponse.class);
    static final DotName LIST = DotName.createSimple(List.class);
    static final DotName UNI = DotName.createSimple(Uni.class);
    static final DotName MULTI = DotName.createSimple(Multi.class);
    static final DotName RUN_ON_VIRTUAL_THREAD = DotName.createSimple(RunOnVirtualThread.class);
    static final DotName BLOCKING = DotName.createSimple(Blocking.class);
    static final DotName NON_BLOCKING = DotName.createSimple(NonBlocking.class);
    static final DotName TRANSACTIONAL = DotName.createSimple("jakarta.transaction.Transactional");
    static final DotName MCP_CONNECTION = DotName.createSimple(McpConnection.class);
    static final DotName REQUEST_ID = DotName.createSimple(RequestId.class);

}
