package io.quarkiverse.mcp.server;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * An audio content provided to or from an LLM.
 *
 * @param data a base64-encoded string representing the audio data (must not be {@code null})
 * @param mimeType the mime type of the audio (must not be {@code null})
 * @param _meta the optional metadata
 */
@JsonInclude(Include.NON_NULL)
public record AudioContent(String data, String mimeType, Map<MetaKey, Object> _meta) implements Content {

    public AudioContent(String data, String mimeType) {
        this(data, mimeType, null);
    }

    public AudioContent {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (mimeType == null) {
            throw new IllegalArgumentException("mimeType must not be null");
        }
    }

    @Override
    public Type type() {
        return Type.AUDIO;
    }

    @Override
    public AudioContent asAudio() {
        return this;
    }

}
