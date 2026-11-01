package airport.visitor;

import airport.model.AirportTree.*;

/** All amounts are integer cents; no floating-point rounding in money calculations. */
public final class RevenueVisitor implements Visitor<Long> {
    @Override public Long visit(Airport airport) { return sum(airport); }
    @Override public Long visit(Terminal terminal) { return sum(terminal); }
    @Override public Long visit(Gate gate) { return sum(gate); }
    @Override public Long visit(Flight flight) { return flight.revenueCents(); }
    @Override public Long visit(Lounge lounge) {
        return Math.multiplyExact((long) lounge.visitors(), lounge.feeCents());
    }
    @Override public Long visit(Shop shop) { return shop.dailyRevenueCents(); }
    @Override public Long visit(ParkingStand stand) { return stand.dailyFeeCents(); }
    @Override public Long visit(ChargingStation station) {
        return Math.multiplyExact((long) station.sessions(), station.feeCents());
    }

    private long sum(Container container) {
        long total = 0;
        for (Element child : container.children()) total = Math.addExact(total, child.accept(this));
        return total;
    }
}
