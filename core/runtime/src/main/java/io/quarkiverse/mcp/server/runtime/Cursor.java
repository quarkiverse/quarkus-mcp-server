package io.quarkiverse.mcp.server.runtime;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;

/**
 * A composite opaque cursor.
 */
record Cursor(Instant createdAt, Instant snapshotTimestamp) {

    static String encode(Instant createdAt, Instant snapshotTimestamp) {
        // long|int|long|int
        ByteBuffer buffer = ByteBuffer.allocate(24);
        buffer.putLong(createdAt.getEpochSecond());
        buffer.putInt(createdAt.getNano());
        buffer.putLong(snapshotTimestamp.getEpochSecond());
        buffer.putInt(snapshotTimestamp.getNano());
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * @return {@code true} if this is an initial cursor, i.e. not obtained from a previous page
     */
    boolean isInitial() {
        return Instant.EPOCH.equals(createdAt);
    }

    static Cursor decode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Blank cursor value");
        }
        byte[] bytes = Base64.getDecoder().decode(value);
        if (bytes.length != 24) {
            throw new IllegalArgumentException("Invalid cursor");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Instant createdAt = Instant.ofEpochSecond(buffer.getLong(), buffer.getInt());
        Instant snapshotTimestamp = Instant.ofEpochSecond(buffer.getLong(), buffer.getInt());
        return new Cursor(createdAt, snapshotTimestamp);
    }

}