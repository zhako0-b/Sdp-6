package airport;

import airport.model.AirportTree.*;
import java.util.List;
import java.util.Map;

/** Fixed model snapshot; operational flight/gate status travels separately as events. */
public final class DemoData {
    private DemoData() { }

    public record Fixture(Airport airport, Map<String, Gate> gates) {
        public Fixture { gates = Map.copyOf(gates); }
    }

    public static Fixture create() {
        Gate first = new Gate("A1", "Gate A1", List.of(
                new Flight("KC101", "Almaty", 120, 1_200_000),
                new Lounge("L1", "Business lounge", 10, 2_500),
                new Shop("S1", "Coffee shop", 40_000),
                new ParkingStand("P1", "Stand A1", 15_000),
                new ChargingStation("C1", "Charging station", 3, 500)));
        Gate second = new Gate("B2", "Gate B2", List.of(
                new Flight("KC202", "Astana", 80, 800_000),
                new Lounge("L2", "Waiting lounge", 5, 2_500),
                new Shop("S2", "Book shop", 20_000),
                new ParkingStand("P2", "Stand B2", 10_000)));
        Airport airport = new Airport("ALA", "Airport", List.of(
                new Terminal("T1", "Terminal 1", List.of(first, second))));
        return new Fixture(airport, Map.of(first.id(), first, second.id(), second));
    }
}
