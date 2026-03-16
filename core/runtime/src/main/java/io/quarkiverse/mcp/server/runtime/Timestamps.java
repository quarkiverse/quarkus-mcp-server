package io.quarkiverse.mcp.server.runtime;

import java.time.Instant;

/**
 * Generates unique monotonically increasing timestamps.
 * <p>
 * If the current time is not after the last generated timestamp, the last timestamp is incremented by 1 nanosecond. This
 * guarantees uniqueness and ordering even when the system clock resolution is coarse.
 */
class Timestamps {

    private static volatile Instant lastTimestamp = Instant.EPOCH;

    synchronized static Instant next() {
        Instant ts = Instant.now();
        if (ts.isAfter(lastTimestamp)) {
            lastTimestamp = ts;
            return ts;
        }
        ts = lastTimestamp.plusNanos(1);
        lastTimestamp = ts;
        return ts;
    }

    /**
     * Returns the later of {@link Instant#now()} and the last generated timestamp, without advancing the internal state.
     * <p>
     * This is useful for snapshot boundaries (e.g. cursor timestamps) that need to be guaranteed to be after all previously
     * generated timestamps but should not interfere with subsequent {@link #next()} calls.
     */
    static Instant nowAfterLast() {
        Instant ts = Instant.now();
        Instant last = lastTimestamp;
        return ts.isAfter(last) ? ts : last;
    }

}
