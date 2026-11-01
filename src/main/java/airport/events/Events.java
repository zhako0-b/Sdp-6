package airport.events;

import java.util.Map;
import java.util.Objects;

/** Immutable messages; producers and listeners share values, not mutable models. */
public final class Events {
    private Events() { }

    public enum Type { FLIGHT_CHANGED, GATE_CHANGED, TRAFFIC_SUMMARY, INSPECTION_REPORT }
    public enum FlightStatus { SCHEDULED, BOARDING, DEPARTED, DELAYED }

    public interface Event {
        Type type();
        String gateId();
    }

    public record FlightChanged(String gateId, String flightId, FlightStatus status)
            implements Event {
        public FlightChanged {
            requireId(gateId);
            requireId(flightId);
            Objects.requireNonNull(status);
        }
        @Override public Type type() { return Type.FLIGHT_CHANGED; }
    }

    public record GateChanged(String gateId, boolean open) implements Event {
        public GateChanged { requireId(gateId); }
        @Override public Type type() { return Type.GATE_CHANGED; }
    }

    /** A cumulative count of notifications, not a count of unique flights. */
    public record TrafficSummary(String gateId, int processedEvents) implements Event {
        public TrafficSummary {
            requireId(gateId);
            if (processedEvents < 0) throw new IllegalArgumentException("Negative count");
        }
        @Override public Type type() { return Type.TRAFFIC_SUMMARY; }
    }

    public record InspectionReport(String gateId, int leafCount, long revenueCents,
                                   Map<String, Integer> inventory) implements Event {
        public InspectionReport {
            requireId(gateId);
            inventory = Map.copyOf(inventory);
        }
        @Override public Type type() { return Type.INSPECTION_REPORT; }
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Missing id");
    }
}
