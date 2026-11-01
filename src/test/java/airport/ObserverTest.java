package airport;

import airport.concurrent.ProducerRunner;
import airport.events.Events.*;
import airport.observer.*;
import airport.observer.Subject.Subscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ObserverTest {
    private final FlightChanged flight = new FlightChanged("A1", "KC101", FlightStatus.BOARDING);

    @Test void deliversOnlyTheSubscribedEventType() {
        EventSource<Event> source = new EventSource<>();
        AtomicInteger count = new AtomicInteger();
        source.subscribe(Type.FLIGHT_CHANGED, event -> count.incrementAndGet());
        source.publish(new GateChanged("A1", false));
        source.publish(flight);
        assertEquals(1, count.get());
    }

    @Test void notifiesFiveListeners() {
        EventSource<Event> source = new EventSource<>();
        AtomicInteger count = new AtomicInteger();
        for (int i = 0; i < 5; i++)
            source.subscribe(Type.FLIGHT_CHANGED, event -> count.incrementAndGet());
        source.publish(flight);
        assertEquals(5, count.get());
    }

    @Test void subscriptionCanBeClosedTwice() {
        EventSource<Event> source = new EventSource<>();
        AtomicInteger count = new AtomicInteger();
        Subscription subscription = source.subscribe(Type.FLIGHT_CHANGED, e -> count.incrementAndGet());
        source.publish(flight);
        subscription.close();
        subscription.close();
        source.publish(flight);
        assertEquals(1, count.get());
    }

    @Test void listenerCanUnsubscribeInsideItsHandler() {
        EventSource<Event> source = new EventSource<>();
        AtomicInteger count = new AtomicInteger();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        subscription.set(source.subscribe(Type.FLIGHT_CHANGED, event -> {
            count.incrementAndGet();
            subscription.get().close();
        }));
        source.publish(flight);
        source.publish(flight);
        assertEquals(1, count.get());
    }

    @Test void listenerAddedInsideHandlerStartsOnNextPublication() {
        EventSource<Event> source = new EventSource<>();
        AtomicInteger late = new AtomicInteger();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        subscription.set(source.subscribe(Type.FLIGHT_CHANGED, event -> {
            source.subscribe(Type.FLIGHT_CHANGED, later -> late.incrementAndGet());
            subscription.get().close();
        }));
        source.publish(flight);
        assertEquals(0, late.get());
        source.publish(flight);
        assertEquals(1, late.get());
    }

    @Test void independentSubscriptionTokensDoNotRemoveEachOther() {
        EventSource<Event> source = new EventSource<>();
        AtomicInteger count = new AtomicInteger();
        airport.observer.Observer<Event> observer = event -> count.incrementAndGet();
        Subscription first = source.subscribe(Type.FLIGHT_CHANGED, observer);
        source.subscribe(Type.FLIGHT_CHANGED, observer);
        first.close();
        source.publish(flight);
        assertEquals(1, count.get());
    }

    @Test void callbackFailureDoesNotSkipOtherListeners() {
        EventSource<Event> source = new EventSource<>();
        AtomicInteger delivered = new AtomicInteger();
        source.subscribe(Type.FLIGHT_CHANGED, event -> { throw new IllegalStateException("broken"); });
        source.subscribe(Type.FLIGHT_CHANGED, event -> delivered.incrementAndGet());
        EventSource.DeliveryException failure =
                assertThrows(EventSource.DeliveryException.class, () -> source.publish(flight));
        assertEquals(1, delivered.get());
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test void rejectsNullSubscriptionArgumentsAndEvents() {
        EventSource<Event> source = new EventSource<>();
        assertThrows(NullPointerException.class, () -> source.subscribe(null, e -> { }));
        assertThrows(NullPointerException.class, () -> source.subscribe(Type.GATE_CHANGED, null));
        assertThrows(NullPointerException.class, () -> source.publish(null));
    }

    @Test void aggregationRepublishesAndKeepsGateCountsSeparate() {
        FlightStatusSource source = new FlightStatusSource();
        TrafficAggregator aggregator = new TrafficAggregator();
        java.util.ArrayList<Event> results = new java.util.ArrayList<>();
        source.subscribe(Type.FLIGHT_CHANGED, aggregator);
        aggregator.subscribe(Type.TRAFFIC_SUMMARY, results::add);
        source.publish(flight);
        source.change("B2", "KC202", FlightStatus.DELAYED);
        source.publish(flight);
        assertEquals(List.of(new TrafficSummary("A1", 1), new TrafficSummary("B2", 1),
                new TrafficSummary("A1", 2)), results);
        assertEquals(2, aggregator.count("A1"));
        assertEquals(1, aggregator.count("B2"));
        assertEquals(0, aggregator.count("missing"));
    }

    @Test void reportDefensivelyCopiesItsMap() {
        Map<String, Integer> original = new java.util.HashMap<>(Map.of("Flight", 1));
        InspectionReport report = new InspectionReport("A1", 1, 100, original);
        original.put("Flight", 99);
        assertEquals(1, report.inventory().get("Flight"));
        assertThrows(UnsupportedOperationException.class, () -> report.inventory().clear());
    }

    @Test void inFlightPublicationKeepsSnapshotAfterConcurrentUnsubscribe() throws Exception {
        EventSource<Event> source = new EventSource<>();
        CountDownLatch captured = new CountDownLatch(1);
        CountDownLatch continueDelivery = new CountDownLatch(1);
        AtomicInteger secondCalls = new AtomicInteger();
        source.subscribe(Type.FLIGHT_CHANGED, event -> {
            captured.countDown();
            try {
                if (!continueDelivery.await(5, TimeUnit.SECONDS))
                    throw new IllegalStateException("Test release timed out");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        });
        Subscription second = source.subscribe(Type.FLIGHT_CHANGED, e -> secondCalls.incrementAndGet());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var publication = executor.submit(() -> source.publish(flight));
            assertTrue(captured.await(5, TimeUnit.SECONDS));
            second.close();
            continueDelivery.countDown();
            publication.get(5, TimeUnit.SECONDS);
            assertEquals(1, secondCalls.get(), "Captured snapshot still contains second listener");
            source.publish(flight);
            assertEquals(1, secondCalls.get(), "Next snapshot excludes second listener");
        } finally {
            continueDelivery.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void fourThreadsTimesOneThousandEventsHaveExactCounts() {
        FlightStatusSource source = new FlightStatusSource();
        TrafficAggregator aggregator = new TrafficAggregator();
        source.subscribe(Type.FLIGHT_CHANGED, aggregator);
        AtomicInteger directDeliveries = new AtomicInteger();
        AtomicInteger summaries = new AtomicInteger();
        for (int listener = 0; listener < 5; listener++)
            source.subscribe(Type.FLIGHT_CHANGED, event -> directDeliveries.incrementAndGet());
        aggregator.subscribe(Type.TRAFFIC_SUMMARY, event -> summaries.incrementAndGet());
        CyclicBarrier start = new CyclicBarrier(4);
        List<Runnable> producers = IntStream.range(0, 4).mapToObj(index -> (Runnable) () -> {
            try { start.await(5, TimeUnit.SECONDS); }
            catch (Exception error) { throw new IllegalStateException(error); }
            for (int i = 0; i < 1_000; i++) source.publish(flight);
        }).toList();
        var executor = Executors.newFixedThreadPool(4);
        try (ProducerRunner runner = new ProducerRunner(executor)) {
            runner.run(producers);
        }
        assertTrue(executor.isTerminated());
        assertEquals(4_000, aggregator.count("A1"));
        assertEquals(4_000, summaries.get());
        assertEquals(20_000, directDeliveries.get());
    }
}
