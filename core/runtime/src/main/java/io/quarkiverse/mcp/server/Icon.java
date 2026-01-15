package io.quarkiverse.mcp.server;

import java.util.List;

/**
 * An icon that can be displayed in a user interface.
 *
 * @param src (must not be {@code null})
 * @param mimeType
 * @param sizes
 * @param theme
 */
public record Icon(String src, String mimeType, List<String> sizes, Theme theme) {

    public Icon(String src, String mimeType) {
        this(src, mimeType, null, null);
    }

    public Icon {
        if (src == null) {
            throw new IllegalArgumentException("src must not be null");
        }
    }

    public enum Theme {
        LIGHT,
        DARK;

        public static Theme from(String val) {
            for (Theme theme : values()) {
                if (theme.toString().equalsIgnoreCase(val)) {
                    return theme;
                }
            }
            throw new IllegalArgumentException("No enum constant:" + val);
        }
    }
}
