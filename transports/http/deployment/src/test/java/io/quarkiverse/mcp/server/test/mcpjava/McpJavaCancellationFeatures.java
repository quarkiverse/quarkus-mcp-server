package io.quarkiverse.mcp.server.test.mcpjava;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.mcpjava.server.Cancellation;
import org.mcpjava.server.Cancellation.OperationCancelledException;
import org.mcpjava.server.tools.Tool;

public class McpJavaCancellationFeatures {

    static final CountDownLatch POLL_LATCH = new CountDownLatch(1);
    static final CountDownLatch SKIP_LATCH = new CountDownLatch(1);

    static final AtomicBoolean CANCELLED = new AtomicBoolean();
    static final AtomicReference<Optional<String>> CANCEL_REASON = new AtomicReference<>();

    @Tool(description = "Polls for cancellation")
    String cancelPoll(Cancellation cancellation) throws InterruptedException {
        POLL_LATCH.countDown();
        int c = 0;
        while (c++ < 20) {
            Cancellation.Result r = cancellation.check();
            if (r.isRequested()) {
                CANCEL_REASON.set(r.reason());
                CANCELLED.set(true);
                throw new OperationCancelledException();
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }
        return "not_cancelled";
    }

    @Tool(description = "Uses skipProcessingIfCancelled")
    String cancelSkip(Cancellation cancellation) throws InterruptedException {
        SKIP_LATCH.countDown();
        try {
            int c = 0;
            while (c++ < 20) {
                cancellation.skipProcessingIfCancelled();
                TimeUnit.MILLISECONDS.sleep(500);
            }
        } catch (OperationCancelledException e) {
            CANCELLED.set(true);
            throw e;
        }
        return "not_cancelled";
    }
}
