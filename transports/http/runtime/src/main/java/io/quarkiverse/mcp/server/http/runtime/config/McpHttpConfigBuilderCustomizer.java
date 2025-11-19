package io.quarkiverse.mcp.server.http.runtime.config;

import java.util.function.Function;

import org.jboss.logging.Logger;

import io.smallrye.config.FallbackConfigSourceInterceptor;
import io.smallrye.config.RelocateConfigSourceInterceptor;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilderCustomizer;

public class McpHttpConfigBuilderCustomizer implements SmallRyeConfigBuilderCustomizer {

    private static final Logger LOG = Logger.getLogger(McpHttpConfigBuilderCustomizer.class);

    static final String SSE_ROOT_PATH = "sse.root-path";
    static final String HTTP_ROOT_PATH = "http.root-path";
    static final String SSE_MESSAGE_ENDPOINT = "sse.message-endpoint.include-query-params";
    static final String HTTP_MESSAGE_ENDPOINT = "http.message-endpoint.include-query-params";

    @Override
    public void configBuilder(SmallRyeConfigBuilder builder) {
        builder.withInterceptors(new FallbackInterceptor(), new RelocateInterceptor());
    }

    static class FallbackInterceptor extends FallbackConfigSourceInterceptor {

        private static final long serialVersionUID = 1L;

        private static final Function<String, String> FALLBACK = name -> {
            if (name.startsWith("quarkus.mcp.server.")) {
                // Make sure to lookup old names for config sources with old keys
                if (name.endsWith(HTTP_ROOT_PATH)) {
                    String fallback = name.substring(0, name.indexOf(HTTP_ROOT_PATH)) + SSE_ROOT_PATH;
                    LOG.debugf("Fallback %s to %s", name, fallback);
                    return fallback;
                } else if (name.endsWith(HTTP_MESSAGE_ENDPOINT)) {
                    String fallback = name.substring(0, name.indexOf(HTTP_MESSAGE_ENDPOINT)) + SSE_MESSAGE_ENDPOINT;
                    LOG.debugf("Fallback %s to %s", name, fallback);
                    return fallback;
                }
            }
            return name;
        };

        public FallbackInterceptor() {
            super(FALLBACK);
        }

    }

    static class RelocateInterceptor extends RelocateConfigSourceInterceptor {

        private static final long serialVersionUID = 1L;

        private static final Function<String, String> RELOCATE = name -> {
            if (name.startsWith("quarkus.mcp.server.")) {
                // Make sure to lookup old names for config sources with old keys
                if (name.endsWith(SSE_ROOT_PATH)) {
                    String relocate = name.substring(0, name.indexOf(SSE_ROOT_PATH)) + HTTP_ROOT_PATH;
                    LOG.debugf("Relocate %s to %s", name, relocate);
                    return relocate;
                } else if (name.endsWith(SSE_MESSAGE_ENDPOINT)) {
                    String relocate = name.substring(0, name.indexOf(SSE_MESSAGE_ENDPOINT)) + HTTP_MESSAGE_ENDPOINT;
                    LOG.debugf("Relocate %s to %s", name, relocate);
                    return relocate;
                }
            }
            return name;
        };

        public RelocateInterceptor() {
            super(RELOCATE);
        }

    }

}
