package io.quarkiverse.mcp.server.runtime;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.FeatureManager.FeatureInfo;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.ResourceContentsEncoder;
import io.quarkiverse.mcp.server.ResourceManager.ResourceInfo;
import io.quarkiverse.mcp.server.ResourceTemplateManager;
import io.quarkiverse.mcp.server.TextResourceContents;

@Singleton
@Priority(0)
public class DefaultResourceContentsEncoder implements ResourceContentsEncoder<Object> {

    @Inject
    ObjectMapper mapper;

    @Override
    public boolean supports(Class<?> runtimeType) {
        return true;
    }

    @Override
    public ResourceContents encode(ResourceContentsData<Object> resourceContentsData) {
        Object data = resourceContentsData.data();
        if (byte[].class.equals(data.getClass())) {
            return new BlobResourceContents(resourceContentsData.uri().value(),
                    BlobResourceContents.toBase64((byte[]) data), extractMimeType(resourceContentsData.featureInfo()),
                    null);
        } else if (String.class.equals(data.getClass())) {
            return new TextResourceContents(resourceContentsData.uri().value(),
                    (String) data, extractMimeType(resourceContentsData.featureInfo()),
                    null);
        }
        try {
            String json = mapper.writeValueAsString(resourceContentsData.data());
            return new TextResourceContents(resourceContentsData.uri().value(),
                    json, extractMimeType(resourceContentsData.featureInfo()),
                    null);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String extractMimeType(FeatureInfo featureInfo) {
        if (featureInfo instanceof ResourceInfo rm) {
            return rm.mimeType();
        } else if (featureInfo instanceof ResourceTemplateManager.ResourceTemplateInfo rti) {
            return rti.mimeType();
        }
        return null;
    }

}
