package io.quarkiverse.mcp.server.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.RequestId;

public class CancellationsImplTest {

    private CancellationsImpl cancellations;
    private RequestId requestId1;
    private RequestId requestId2;
    private String connectionId1;
    private String connectionId2;

    @BeforeEach
    public void setUp() {
        cancellations = new CancellationsImpl();
        requestId1 = new RequestId("request-1");
        requestId2 = new RequestId("request-2");
        connectionId1 = "connection-1";
        connectionId2 = "connection-2";
    }

    @Test
    public void testIsCancelledReturnsFalseWhenNoRequestsCancelled() {
        assertFalse(cancellations.isCancelled(requestId1));
        assertFalse(cancellations.isCancelled(connectionId1, requestId1));
    }

    @Test
    public void testCancelRequestMarksRequestAsCancelled() {
        cancellations.cancelRequest(connectionId1, requestId1);

        assertTrue(cancellations.isCancelled(requestId1));
        assertTrue(cancellations.isCancelled(connectionId1, requestId1));
    }

    @Test
    public void testCancelRequestWithMultipleConnections() {
        cancellations.cancelRequest(connectionId1, requestId1);
        cancellations.cancelRequest(connectionId2, requestId2);

        assertTrue(cancellations.isCancelled(requestId1));
        assertTrue(cancellations.isCancelled(requestId2));
        assertTrue(cancellations.isCancelled(connectionId1, requestId1));
        assertTrue(cancellations.isCancelled(connectionId2, requestId2));
        assertFalse(cancellations.isCancelled(connectionId1, requestId2));
        assertFalse(cancellations.isCancelled(connectionId2, requestId1));
    }

    @Test
    public void testCancelMultipleRequestsOnSameConnection() {
        cancellations.cancelRequest(connectionId1, requestId1);
        cancellations.cancelRequest(connectionId1, requestId2);

        assertTrue(cancellations.isCancelled(connectionId1, requestId1));
        assertTrue(cancellations.isCancelled(connectionId1, requestId2));
    }

    @Test
    public void testRemoveRequestRemovesCancelledRequest() {
        cancellations.cancelRequest(connectionId1, requestId1);
        assertTrue(cancellations.isCancelled(connectionId1, requestId1));

        cancellations.removeRequest(connectionId1, requestId1);
        assertFalse(cancellations.isCancelled(connectionId1, requestId1));
    }

    @Test
    public void testRemoveRequestWithMultipleRequestsOnConnection() {
        cancellations.cancelRequest(connectionId1, requestId1);
        cancellations.cancelRequest(connectionId1, requestId2);

        cancellations.removeRequest(connectionId1, requestId1);

        assertFalse(cancellations.isCancelled(connectionId1, requestId1));
        assertTrue(cancellations.isCancelled(connectionId1, requestId2));
    }

    @Test
    public void testRemoveRequestFromNonExistentConnection() {
        // Should not throw exception
        cancellations.removeRequest("non-existent", requestId1);
        assertFalse(cancellations.isCancelled("non-existent", requestId1));
    }

    @Test
    public void testRemoveNonExistentRequest() {
        cancellations.cancelRequest(connectionId1, requestId1);

        // Should not throw exception
        cancellations.removeRequest(connectionId1, requestId2);

        assertTrue(cancellations.isCancelled(connectionId1, requestId1));
    }

    @Test
    public void testCleanupConnectionRemovesAllCancellations() {
        cancellations.cancelRequest(connectionId1, requestId1);
        cancellations.cancelRequest(connectionId1, requestId2);
        cancellations.cancelRequest(connectionId2, requestId1);

        cancellations.cleanupConnection(connectionId1);

        assertFalse(cancellations.isCancelled(connectionId1, requestId1));
        assertFalse(cancellations.isCancelled(connectionId1, requestId2));
        assertTrue(cancellations.isCancelled(connectionId2, requestId1));
    }

    @Test
    public void testCleanupNonExistentConnection() {
        // Should not throw exception
        cancellations.cleanupConnection("non-existent");
    }

    @Test
    public void testCancelSameRequestMultipleTimes() {
        cancellations.cancelRequest(connectionId1, requestId1);
        cancellations.cancelRequest(connectionId1, requestId1);

        assertTrue(cancellations.isCancelled(connectionId1, requestId1));

        cancellations.removeRequest(connectionId1, requestId1);
        assertFalse(cancellations.isCancelled(connectionId1, requestId1));
    }
}
