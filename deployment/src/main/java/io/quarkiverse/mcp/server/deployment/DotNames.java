package io.quarkiverse.mcp.server.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceContent;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
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
    static final DotName TOOL_ARG = DotName.createSimple(ToolArg.class);
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
    static final DotName CONTENT = DotName.createSimple(Content.class);
    static final DotName TEXT_CONTENT = DotName.createSimple(TextContent.class);
    static final DotName IMAGE_CONTENT = DotName.createSimple(ImageContent.class);
    static final DotName RESOURCE_CONTENT = DotName.createSimple(ResourceContent.class);
    static final DotName RESOURCE = DotName.createSimple(Resource.class);
    static final DotName RESOURCE_RESPONSE = DotName.createSimple(ResourceResponse.class);
    static final DotName RESOURCE_CONTENS = DotName.createSimple(ResourceContents.class);
    static final DotName TEXT_RESOURCE_CONTENS = DotName.createSimple(TextResourceContents.class);
    static final DotName BLOB_RESOURCE_CONTENS = DotName.createSimple(BlobResourceContents.class);
    static final DotName STRING = DotName.createSimple(String.class);

}
