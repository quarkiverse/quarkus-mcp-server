package io.quarkiverse.mcp.server.deployment;

import java.util.Map;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodParameterInfo;

import io.quarkiverse.mcp.server.runtime.FeatureArgument.Provider;

final class FeatureArguments {

    private FeatureArguments() {
    }

    private static final Map<DotName, Provider> PROVIDERS = Map.ofEntries(
            Map.entry(DotNames.MCP_CONNECTION, Provider.MCP_CONNECTION),
            Map.entry(DotNames.REQUEST_ID, Provider.REQUEST_ID),
            Map.entry(DotNames.MCP_LOG, Provider.MCP_LOG),
            Map.entry(DotNames.REQUEST_URI, Provider.REQUEST_URI),
            Map.entry(DotNames.PROGRESS, Provider.PROGRESS),
            Map.entry(DotNames.ROOTS, Provider.ROOTS),
            Map.entry(DotNames.SAMPLING, Provider.SAMPLING),
            Map.entry(DotNames.CANCELLATION, Provider.CANCELLATION),
            Map.entry(DotNames.RAW_MESSAGE, Provider.RAW_MESSAGE),
            Map.entry(DotNames.COMPLETE_CONTEXT, Provider.COMPLETE_CONTEXT),
            Map.entry(DotNames.META, Provider.META),
            Map.entry(DotNames.ELICITATION, Provider.ELICITATION),
            Map.entry(DotNames.MCPJAVA_PROGRESS, Provider.MCPJAVA_PROGRESS),
            Map.entry(DotNames.MCPJAVA_CANCELLATION, Provider.MCPJAVA_CANCELLATION),
            Map.entry(DotNames.MCPJAVA_MCP_REQUEST, Provider.MCPJAVA_MCP_REQUEST),
            Map.entry(DotNames.MCPJAVA_COMPLETION_CONTEXT, Provider.MCPJAVA_COMPLETION_CONTEXT));

    static Provider providerFrom(org.jboss.jandex.Type type) {
        return PROVIDERS.getOrDefault(type.name(), Provider.PARAMS);
    }

    static boolean isOptionalType(DotName name) {
        return DotNames.OPTIONAL.equals(name)
                || DotNames.OPTIONAL_INT.equals(name)
                || DotNames.OPTIONAL_LONG.equals(name)
                || DotNames.OPTIONAL_DOUBLE.equals(name);
    }

    static boolean hasDefaultValue(MethodParameterInfo param) {
        AnnotationInstance anno = param.annotation(DotNames.TOOL_ARG);
        if (anno == null) {
            anno = param.annotation(DotNames.PROMPT_ARG);
        }
        if (anno == null) {
            anno = param.annotation(DotNames.MCPJAVA_TOOL_ARG);
        }
        if (anno == null) {
            anno = param.annotation(DotNames.MCPJAVA_PROMPT_ARG);
        }
        AnnotationValue defaultValueValue = anno != null ? anno.value("defaultValue") : null;
        return defaultValueValue != null;
    }

}
