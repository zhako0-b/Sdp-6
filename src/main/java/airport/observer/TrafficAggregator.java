package airport.observer;

import airport.events.Events.Event;
import airport.events.Events.FlightChanged;
import airport.events.Events.TrafficSummary;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** A listener and a subject: flight notifications -> cumulative gate summaries. */
public final class TrafficAggregator extends EventSource<Event> implements Observer<Event> {
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    @Override
    public void onEvent(Event event) {
        if (event instanceof FlightChanged flight) {
            int count = counts.computeIfAbsent(flight.gateId(), key -> new AtomicInteger())
                    .incrementAndGet();
            publish(new TrafficSummary(flight.gateId(), count));
        }
    }

    public int count(String gateId) {
        AtomicInteger count = counts.get(gateId);
        return count == null ? 0 : count.get();
    }
}
