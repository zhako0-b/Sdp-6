package airport.listener;

import airport.events.Events.*;
import airport.model.AirportTree.Gate;
import airport.observer.EventSource;
import airport.observer.Observer;
import airport.visitor.InventoryVisitor;
import airport.visitor.RevenueVisitor;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Integration: gate closure -> visitors on that gate -> immutable report event. */
public final class InspectionService implements Observer<Event> {
    private final Map<String, Gate> gates;
    private final Supplier<InventoryVisitor> inventoryFactory;
    private final RevenueVisitor revenueVisitor;
    private final EventSource<Event> reports;

    public InspectionService(Map<String, Gate> gates, Supplier<InventoryVisitor> inventoryFactory,
                             RevenueVisitor revenueVisitor, EventSource<Event> reports) {
        this.gates = Map.copyOf(gates);
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory);
        this.revenueVisitor = Objects.requireNonNull(revenueVisitor);
        this.reports = Objects.requireNonNull(reports);
    }

    @Override public void onEvent(Event event) {
        if (!(event instanceof GateChanged change) || change.open()) return;
        Gate gate = gates.get(change.gateId());
        if (gate == null) throw new IllegalArgumentException("Unknown gate: " + change.gateId());
        InventoryVisitor.Inventory inventory = gate.accept(inventoryFactory.get());
        long revenue = gate.accept(revenueVisitor);
        reports.publish(new InspectionReport(gate.id(), inventory.leafCount(),
                revenue, inventory.counts()));
    }
}
