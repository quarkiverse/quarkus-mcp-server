package io.quarkiverse.mcp.server.http.runtime;

/**
 * Shared validation helpers for the {@code x-mcp-header} feature (custom headers from tool parameters).
 * <p>
 * Used both at build time by the deployment processor (for method-backed tools annotated with
 * {@link io.quarkiverse.mcp.server.http.McpParamHeader @McpParamHeader}) and at runtime by
 * {@link McpParamHeaderObserver} (for programmatically registered tools).
 */
public final class HttpParamHeaders {

    private HttpParamHeaders() {
    }

    /**
     * Returns {@code true} if the given value is a valid HTTP field-name token as defined by
     * RFC 9110 Section 5.6.2. An empty value is considered a valid (empty) token; callers that
     * disallow empty header names must check for emptiness separately.
     *
     * @param value the value to validate
     * @return {@code true} if every character is a {@code tchar}
     */
    public static boolean isValidHttpToken(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!isTchar(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // RFC 9110 Section 5.6.2: tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
    // "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
    private static boolean isTchar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '!' || c == '#' || c == '$' || c == '%' || c == '&' || c == '\'' || c == '*'
                || c == '+' || c == '-' || c == '.' || c == '^' || c == '_' || c == '`' || c == '|' || c == '~';
    }
}
