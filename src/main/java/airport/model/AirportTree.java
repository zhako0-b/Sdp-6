package airport.model;

import airport.visitor.Visitor;
import java.util.List;

/** Data-only records. accept selects an overload; traversal belongs to visitors. */
public final class AirportTree {
    private AirportTree() { }

    public interface Element {
        String id();
        String name();
        <R> R accept(Visitor<R> visitor);
    }

    public interface Container extends Element {
        List<Element> children();
    }

    public record Airport(String id, String name, List<Element> children) implements Container {
        public Airport { validate(id, name); children = List.copyOf(children); }
        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visit(this); }
    }

    public record Terminal(String id, String name, List<Element> children) implements Container {
        public Terminal { validate(id, name); children = List.copyOf(children); }
        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visit(this); }
    }

    public record Gate(String id, String name, List<Element> children) implements Container {
        public Gate { validate(id, name); children = List.copyOf(children); }
        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visit(this); }
    }

    public record Flight(String id, String name, int passengers, long revenueCents)
            implements Element {
        public Flight { validate(id, name); nonNegative(passengers); nonNegative(revenueCents); }
        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visit(this); }
    }

    public record Lounge(String id, String name, int visitors, long feeCents) implements Element {
        public Lounge { validate(id, name); nonNegative(visitors); nonNegative(feeCents); }
        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visit(this); }
    }

    public record Shop(String id, String name, long dailyRevenueCents) implements Element {
        public Shop { validate(id, name); nonNegative(dailyRevenueCents); }
        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visit(this); }
    }

    public record ParkingStand(String id, String name, long dailyFeeCents) implements Element {
        public ParkingStand { validate(id, name); nonNegative(dailyFeeCents); }
        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visit(this); }
    }

    public record ChargingStation(String id, String name, int sessions, long feeCents)
            implements Element {
        public ChargingStation { validate(id, name); nonNegative(sessions); nonNegative(feeCents); }
        @Override public <R> R accept(Visitor<R> visitor) { return visitor.visit(this); }
    }

    private static void validate(String id, String name) {
        if (id == null || id.isBlank() || name == null || name.isBlank())
            throw new IllegalArgumentException("Element needs an id and name");
    }

    private static void nonNegative(long value) {
        if (value < 0) throw new IllegalArgumentException("Negative element value");
    }
}
