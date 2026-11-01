package airport.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Owns the injected executor's lifecycle; all finite producer jobs are drained. */
public final class ProducerRunner implements AutoCloseable {
    private final ExecutorService executor;

    public ProducerRunner(ExecutorService executor) { this.executor = Objects.requireNonNull(executor); }

    public void run(List<Runnable> producers) {
        List<Future<?>> jobs = new ArrayList<>();
        for (Runnable producer : producers) jobs.add(executor.submit(producer));
        boolean interrupted = false;
        IllegalStateException failure = null;
        for (Future<?> job : jobs) {
            boolean finished = false;
            while (!finished) {
                try {
                    job.get();
                    finished = true;
                } catch (InterruptedException error) {
                    interrupted = true; // Drain accepted events before restoring the flag.
                } catch (ExecutionException error) {
                    if (failure == null) failure = new IllegalStateException("Producer failed");
                    failure.addSuppressed(error.getCause());
                    finished = true;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (failure != null) throw failure;
    }

    @Override public void close() {
        executor.shutdown();
        boolean interrupted = false;
        boolean terminated = false;
        while (!terminated) {
            try {
                terminated = executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
