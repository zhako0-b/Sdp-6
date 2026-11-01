package airport;

import airport.model.AirportTree.*;
import airport.visitor.PassengerLoadVisitor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PassengerLoadVisitorTest {
    @Test void sumsFlightPassengersAndLoungeVisitors() {
        // A1: 120+10=130; B2: 80+5=85; total=215.
        var fixture = DemoData.create();
        var visitor = new PassengerLoadVisitor();
        assertEquals(215, fixture.airport().accept(visitor));
        assertEquals(130, fixture.gates().get("A1").accept(visitor));
        assertEquals(85, fixture.gates().get("B2").accept(visitor));
    }

    @Test void commercialObjectsDoNotCountAsPassengers() {
        Gate gate = new Gate("G", "Gate", List.of(
                new Shop("S", "Shop", 50), new ParkingStand("P", "Stand", 100)));
        assertEquals(0, gate.accept(new PassengerLoadVisitor()));
    }
}
