package airport.visitor;

import airport.model.AirportTree.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Includes matching containers too; result order is deterministic depth-first preorder. */
public final class FilterVisitor implements Visitor<List<Element>> {
    private final Predicate<Element> predicate;

    public FilterVisitor(Predicate<Element> predicate) {
        this.predicate = Objects.requireNonNull(predicate);
    }

    @Override public List<Element> visit(Airport airport) { return filter(airport); }
    @Override public List<Element> visit(Terminal terminal) { return filter(terminal); }
    @Override public List<Element> visit(Gate gate) { return filter(gate); }
    @Override public List<Element> visit(Flight flight) { return match(flight); }
    @Override public List<Element> visit(Lounge lounge) { return match(lounge); }
    @Override public List<Element> visit(Shop shop) { return match(shop); }
    @Override public List<Element> visit(ParkingStand stand) { return match(stand); }
    @Override public List<Element> visit(ChargingStation station) { return match(station); }

    private List<Element> filter(Container container) {
        List<Element> result = new ArrayList<>(match(container));
        for (Element child : container.children()) result.addAll(child.accept(this));
        return List.copyOf(result);
    }

    private List<Element> match(Element element) {
        return predicate.test(element) ? List.of(element) : List.of();
    }
}
