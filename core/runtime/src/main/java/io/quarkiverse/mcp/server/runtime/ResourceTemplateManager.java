package io.quarkiverse.mcp.server.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.mcp.server.ResourceResponse;
import io.vertx.core.Vertx;

@Singleton
public class ResourceTemplateManager extends FeatureManager<ResourceResponse> {

    final Map<String, ResourceTemplateMetadata> templates;

    ResourceTemplateManager(McpMetadata metadata, Vertx vertx, ObjectMapper mapper) {
        super(vertx, mapper);
        this.templates = metadata.resourceTemplates().stream().collect(Collectors.toMap(m -> m.info().name(),
                m -> new ResourceTemplateMetadata(createMatcherFromUriTemplate(m.info().uri()), m)));
    }

    VariableMatcher getVariableMatcher(String name) {
        return templates.get(name).variableMatcher();
    }

    @Override
    protected FeatureMetadata<ResourceResponse> getMetadata(String identifier) {
        // This method is used by ResourceManager during "resources/read"
        // We need to iterate over all templates and find the matching URI template
        for (ResourceTemplateMetadata t : templates.values()) {
            if (t.variableMatcher().matches(identifier)) {
                return t.metadata();
            }
        }
        return null;
    }

    /**
     *
     * @return the list of resource templates sorted by name asc
     */
    public List<FeatureMetadata<ResourceResponse>> list() {
        return templates.values().stream().map(ResourceTemplateMetadata::metadata).sorted().toList();
    }

    @Override
    protected McpException notFound(String id) {
        return new McpException("Invalid resource uri: " + id, JsonRPC.RESOURCE_NOT_FOUND);
    }

    static VariableMatcher createMatcherFromUriTemplate(String uriTemplate) {
        // Find variables
        List<String> variables = new ArrayList<>();
        Matcher m = Pattern.compile("\\{(\\w+)\\}").matcher(uriTemplate);
        StringBuilder uriRegex = new StringBuilder();
        while (m.find()) {
            variables.add(m.group(1));
            m.appendReplacement(uriRegex, "([^/]+)");
        }
        m.appendTail(uriRegex);
        return new VariableMatcher(Pattern.compile(uriRegex.toString()), variables);
    }

    record ResourceTemplateMetadata(VariableMatcher variableMatcher, FeatureMetadata<ResourceResponse> metadata) {
    }

    record VariableMatcher(Pattern pattern, List<String> variables) {

        boolean matches(String uri) {
            return pattern.matcher(uri).matches();
        }

        Map<String, Object> matchVariables(String uri) {
            Map<String, Object> ret = new HashMap<>();
            Matcher m = pattern.matcher(uri);
            if (m.matches()) {
                for (int i = 0; i < m.groupCount(); i++) {
                    ret.put(variables.get(i), m.group(i + 1));
                }
            }
            return ret;
        }

    }

}
