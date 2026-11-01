package airport.visitor;

import airport.model.AirportTree.*;

public interface Visitor<R> {
    R visit(Airport airport);
    R visit(Terminal terminal);
    R visit(Gate gate);
    R visit(Flight flight);
    R visit(Lounge lounge);
    R visit(Shop shop);
    R visit(ParkingStand stand);
    R visit(ChargingStation station);
}
