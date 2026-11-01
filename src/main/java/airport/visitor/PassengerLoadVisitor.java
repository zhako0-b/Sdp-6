package airport.visitor;

import airport.model.AirportTree.*;

/** A new operation added without editing the stable element classes or Visitor interface. */
public final class PassengerLoadVisitor implements Visitor<Integer> {
    @Override public Integer visit(Airport airport) { return sum(airport); }
    @Override public Integer visit(Terminal terminal) { return sum(terminal); }
    @Override public Integer visit(Gate gate) { return sum(gate); }
    @Override public Integer visit(Flight flight) { return flight.passengers(); }
    @Override public Integer visit(Lounge lounge) { return lounge.visitors(); }
    @Override public Integer visit(Shop shop) { return 0; }
    @Override public Integer visit(ParkingStand stand) { return 0; }
    @Override public Integer visit(ChargingStation station) { return 0; }

    private int sum(Container container) {
        int total = 0;
        for (Element child : container.children()) total = Math.addExact(total, child.accept(this));
        return total;
    }
}
