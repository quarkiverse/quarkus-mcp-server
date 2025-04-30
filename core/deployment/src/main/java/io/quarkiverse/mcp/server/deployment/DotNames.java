package io.quarkiverse.mcp.server.deployment;

import java.util.List;
import java.util.Optional;

import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.CompleteArg;
import io.quarkiverse.mcp.server.CompletePrompt;
import io.quarkiverse.mcp.server.CompleteResourceTemplate;
import io.quarkiverse.mcp.server.CompletionResponse;
import io.quarkiverse.mcp.server.Content;
import io.quarkiverse.mcp.server.DefaultValueConverter;
import io.quarkiverse.mcp.server.EmbeddedResource;
import io.quarkiverse.mcp.server.ImageContent;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.McpLog;
import io.quarkiverse.mcp.server.Notification;
import io.quarkiverse.mcp.server.Progress;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptManager;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.PromptResponse;
import io.quarkiverse.mcp.server.RequestId;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.ResourceTemplateArg;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.Roots;
import io.quarkiverse.mcp.server.Sampling;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolManager;
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
    static final DotName MCP_LOG = DotName.createSimple(McpLog.class);
    static final DotName REQUEST_ID = DotName.createSimple(RequestId.class);
    static final DotName REQUEST_URI = DotName.createSimple(RequestUri.class);
    static final DotName CONTENT = DotName.createSimple(Content.class);
    static final DotName TEXT_CONTENT = DotName.createSimple(TextContent.class);
    static final DotName IMAGE_CONTENT = DotName.createSimple(ImageContent.class);
    static final DotName EMBEDDED_RESOURCE = DotName.createSimple(EmbeddedResource.class);
    static final DotName RESOURCE = DotName.createSimple(Resource.class);
    static final DotName RESOURCE_RESPONSE = DotName.createSimple(ResourceResponse.class);
    static final DotName RESOURCE_CONTENTS = DotName.createSimple(ResourceContents.class);
    static final DotName TEXT_RESOURCE_CONTENTS = DotName.createSimple(TextResourceContents.class);
    static final DotName BLOB_RESOURCE_CONTENTS = DotName.createSimple(BlobResourceContents.class);
    static final DotName STRING = DotName.createSimple(String.class);
    static final DotName COMPLETE_PROMPT = DotName.createSimple(CompletePrompt.class);
    static final DotName COMPLETE_RESOURCE_TEMPLATE = DotName.createSimple(CompleteResourceTemplate.class);
    static final DotName COMPLETE_ARG = DotName.createSimple(CompleteArg.class);
    static final DotName COMPLETE_RESPONSE = DotName.createSimple(CompletionResponse.class);
    static final DotName RESOURCE_TEMPLATE = DotName.createSimple(ResourceTemplate.class);
    static final DotName RESOURCE_TEMPLATE_ARG = DotName.createSimple(ResourceTemplateArg.class);
    static final DotName RESOURCE_MANAGER = DotName.createSimple(ResourceManager.class);
    static final DotName RESOURCE_TEMPLATE_MANAGER = DotName.createSimple(ResourceTemplateManager.class);
    static final DotName TOOL_MANAGER = DotName.createSimple(ToolManager.class);
    static final DotName PROMPT_MANAGER = DotName.createSimple(PromptManager.class);
    static final DotName PROGRESS = DotName.createSimple(Progress.class);
    static final DotName NOTIFICATION = DotName.createSimple(Notification.class);
    static final DotName ROOTS = DotName.createSimple(Roots.class);
    static final DotName SAMPLING = DotName.createSimple(Sampling.class);
    static final DotName DEFAULT_VALUE_CONVERTER = DotName.createSimple(DefaultValueConverter.class);

    static final DotName LANGCHAIN4J_TOOL = DotName.createSimple("dev.langchain4j.agent.tool.Tool");
    static final DotName LANGCHAIN4J_P = DotName.createSimple("dev.langchain4j.agent.tool.P");

    static final DotName OPTIONAL = DotName.createSimple(Optional.class);

}
