package airport.visitor;

import airport.model.AirportTree.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stateful: create one instance for one inspection; never share across threads. */
public final class InventoryVisitor implements Visitor<InventoryVisitor.Inventory> {
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private int leafCount;

    public record Inventory(int leafCount, Map<String, Integer> counts) {
        public Inventory { counts = Map.copyOf(counts); }
    }

    @Override public Inventory visit(Airport airport) { return collect(airport); }
    @Override public Inventory visit(Terminal terminal) { return collect(terminal); }
    @Override public Inventory visit(Gate gate) { return collect(gate); }
    @Override public Inventory visit(Flight flight) { return leaf("Flight"); }
    @Override public Inventory visit(Lounge lounge) { return leaf("Lounge"); }
    @Override public Inventory visit(Shop shop) { return leaf("Shop"); }
    @Override public Inventory visit(ParkingStand stand) { return leaf("ParkingStand"); }
    @Override public Inventory visit(ChargingStation station) { return leaf("ChargingStation"); }

    private Inventory collect(Container container) {
        for (Element child : container.children()) child.accept(this);
        return snapshot();
    }

    private Inventory leaf(String type) {
        counts.merge(type, 1, Integer::sum);
        leafCount++;
        return snapshot();
    }

    private Inventory snapshot() { return new Inventory(leafCount, counts); }
}
