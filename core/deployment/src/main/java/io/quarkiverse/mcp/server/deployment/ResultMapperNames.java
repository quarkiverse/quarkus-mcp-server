package io.quarkiverse.mcp.server.deployment;

import static io.quarkiverse.mcp.server.runtime.Feature.PROMPT_COMPLETE;
import static io.quarkiverse.mcp.server.runtime.Feature.RESOURCE_TEMPLATE_COMPLETE;

import java.util.function.Function;

import org.jboss.jandex.DotName;

import io.quarkiverse.mcp.server.runtime.Feature;

final class ResultMapperNames {

    private ResultMapperNames() {
    }

    static String createMapperClassSimpleName(Feature feature, org.jboss.jandex.Type returnType,
            DotName baseType, Function<DotName, String> componentMapper) {
        if (returnType.name().equals(baseType)) {
            return "ToUni";
        }
        org.jboss.jandex.Type type = returnType;
        StringBuilder ret;
        if (feature == PROMPT_COMPLETE || feature == RESOURCE_TEMPLATE_COMPLETE) {
            ret = new StringBuilder("Complete");
        } else {
            String f = feature.toString();
            ret = new StringBuilder().append(f.charAt(0)).append(f.substring(1).toLowerCase());
        }
        if (DotNames.isAsyncType(type.name())) {
            type = type.asParameterizedType().arguments().get(0);
            if (type.name().equals(baseType)) {
                return "Identity";
            }
            ret.append("Uni");
        }
        if (DotNames.LIST.equals(type.name())) {
            type = type.asParameterizedType().arguments().get(0);
            ret.append("List");
        }
        ret.append(componentMapper.apply(type.name()));

        return ret.toString();
    }

    static boolean isContent(DotName typeName) {
        return DotNames.CONTENT.equals(typeName)
                || DotNames.TEXT_CONTENT.equals(typeName)
                || DotNames.IMAGE_CONTENT.equals(typeName)
                || DotNames.EMBEDDED_RESOURCE.equals(typeName)
                || DotNames.RESOURCE_LINK.equals(typeName)
                || DotNames.AUDIO_CONTENT.equals(typeName);
    }

}
