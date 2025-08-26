package io.quarkiverse.mcp.server;

import java.util.Map;

/**
 * An audio content provided to or from an LLM.
 *
 * @param data a base64-encoded string representing the audio data (must not be {@code null})
 * @param mimeType the mime type of the audio (must not be {@code null})
 * @param _meta the optional metadata (may be {@code null})
 * @param annotations the optional annotations (may be {@code null})
 */
public record AudioContent(String data, String mimeType, Map<MetaKey, Object> _meta, Annotations annotations)
        implements
            Content {

    public AudioContent(String data, String mimeType) {
        this(data, mimeType, null, null);
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
