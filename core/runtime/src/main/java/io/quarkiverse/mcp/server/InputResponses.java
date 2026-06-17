package io.quarkiverse.mcp.server;

import java.util.List;

/**
 * Provides access to the {@code inputResponses} from a client retry as part of the Multi Round-Trip Requests (MRTR) pattern.
 * <p>
 * When a server feature method throws an {@link InputRequiredException}, the client may retry the original request with the
 * gathered input. This object provides typed access to those responses.
 * <p>
 * Obtain an instance via {@link Elicitation#inputResponses()}, {@link Sampling#inputResponses()}, or
 * {@link Roots#inputResponses()}. The returned object is never {@code null} but may be {@linkplain #isEmpty() empty} on the
 * initial request.
 *
 * @see InputRequiredException
 * @see Elicitation#inputResponses()
 * @see Sampling#inputResponses()
 * @see Roots#inputResponses()
 */
public interface InputResponses {

    /**
     * @return {@code true} if no input responses are present (i.e. this is the initial request)
     */
    boolean isEmpty();

    /**
     * @param key the server-assigned identifier used in {@link InputRequiredException.Builder}
     * @return {@code true} if a response with the given key exists
     */
    boolean has(String key);

    /**
     * @param key the server-assigned identifier
     * @return the elicitation response, or {@code null} if not present
     */
    ElicitationResponse getElicitationResponse(String key);

    /**
     * @param key the server-assigned identifier
     * @return the sampling response, or {@code null} if not present
     */
    SamplingResponse getSamplingResponse(String key);

    /**
     * @param key the server-assigned identifier
     * @return the list of roots, or {@code null} if not present
     */
    List<Root> getRootsResponse(String key);

}
