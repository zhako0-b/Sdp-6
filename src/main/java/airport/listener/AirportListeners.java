package airport.listener;

import airport.events.Events.*;
import airport.observer.Observer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Related small listeners are grouped in one file; each has its own responsibility. */
public final class AirportListeners {
    private AirportListeners() { }

    public static final class DepartureBoard implements Observer<Event> {
        private final ConcurrentHashMap<String, FlightStatus> statuses = new ConcurrentHashMap<>();
        @Override public void onEvent(Event event) {
            if (event instanceof FlightChanged flight)
                statuses.put(flight.flightId(), flight.status());
        }
        public Map<String, FlightStatus> snapshot() { return Map.copyOf(statuses); }
    }

    /** Console demo outbox: it does not contact a real SMS provider. */
    public static final class PassengerSms implements Observer<Event> {
        private final ConcurrentLinkedQueue<String> outbox = new ConcurrentLinkedQueue<>();
        @Override public void onEvent(Event event) {
            if (event instanceof FlightChanged flight)
                outbox.add(flight.flightId() + ": " + flight.status());
        }
        public List<String> messages() { return List.copyOf(outbox); }
    }

    public static final class GateManager implements Observer<Event> {
        private final ConcurrentHashMap<String, Boolean> availability = new ConcurrentHashMap<>();
        @Override public void onEvent(Event event) {
            if (event instanceof GateChanged gate) availability.put(gate.gateId(), gate.open());
        }
        public Map<String, Boolean> snapshot() { return Map.copyOf(availability); }
    }

    public static final class BaggageSystem implements Observer<Event> {
        private final AtomicInteger handled = new AtomicInteger();
        @Override public void onEvent(Event event) {
            if (event instanceof FlightChanged) handled.incrementAndGet();
        }
        public int handledEvents() { return handled.get(); }
    }

    public static final class AuditLog implements Observer<Event> {
        private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
        @Override public void onEvent(Event event) { events.add(event); }
        public List<Event> snapshot() { return List.copyOf(events); }
    }

    public static final class ReportCollector implements Observer<Event> {
        private final ConcurrentLinkedQueue<InspectionReport> reports = new ConcurrentLinkedQueue<>();
        @Override public void onEvent(Event event) {
            if (event instanceof InspectionReport report) reports.add(report);
        }
        public List<InspectionReport> reports() { return List.copyOf(reports); }
    }
}
