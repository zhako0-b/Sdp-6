package airport;

import airport.concurrent.ProducerRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ProducerRunnerTest {
    @Test void gracefulCloseDrainsAcceptedJobsAndTerminatesThreads() {
        var executor = Executors.newFixedThreadPool(3);
        AtomicInteger completed = new AtomicInteger();
        try (ProducerRunner runner = new ProducerRunner(executor)) {
            // These are already accepted jobs; close must wait even without run().
            for (int i = 0; i < 100; i++) executor.submit(completed::incrementAndGet);
        }
        assertEquals(100, completed.get());
        assertTrue(executor.isTerminated());
    }

    @Test void producerFailureStillWaitsForOtherJobs() {
        AtomicInteger completed = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(3);
        try (ProducerRunner runner = new ProducerRunner(executor)) {
            var failure = assertThrows(IllegalStateException.class, () -> runner.run(List.of(
                    () -> { throw new IllegalArgumentException("bad input"); },
                    completed::incrementAndGet,
                    completed::incrementAndGet)));
            assertEquals(2, completed.get());
            assertEquals(1, failure.getSuppressed().length);
        }
        assertTrue(executor.isTerminated());
    }

    @Test void oneConfiguredWorkerCanExecuteThreeProducerJobs() {
        AtomicInteger completed = new AtomicInteger();
        try (ProducerRunner runner = new ProducerRunner(Executors.newFixedThreadPool(1))) {
            runner.run(List.of(completed::incrementAndGet, completed::incrementAndGet,
                    completed::incrementAndGet));
        }
        assertEquals(3, completed.get());
    }

    @Test void interruptedCallerStillDrainsAndRestoresInterrupt() throws Exception {
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean restored = new AtomicBoolean();
        var executor = Executors.newFixedThreadPool(3);
        Thread caller = new Thread(() -> {
            Thread.currentThread().interrupt();
            try (ProducerRunner runner = new ProducerRunner(executor)) {
                runner.run(List.of(completed::incrementAndGet, completed::incrementAndGet));
            }
            restored.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        caller.join(5_000);
        assertFalse(caller.isAlive());
        assertTrue(executor.isTerminated());
        assertEquals(2, completed.get());
        assertTrue(restored.get());
    }
}
