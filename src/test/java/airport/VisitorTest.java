package airport;

import airport.model.AirportTree.*;
import airport.visitor.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VisitorTest {
    private final DemoData.Fixture fixture = DemoData.create();

    @Test void wholeAirportRevenueMatchesHandCalculation() {
        // A1: 1,200,000 + 10*2,500 + 40,000 + 15,000 + 3*500 = 1,281,500
        // B2: 800,000 + 5*2,500 + 20,000 + 10,000 = 842,500
        assertEquals(2_124_000L, fixture.airport().accept(new RevenueVisitor()));
    }

    @Test void subtreeRevenueExcludesOtherGate() {
        assertEquals(1_281_500L, fixture.gates().get("A1").accept(new RevenueVisitor()));
        assertEquals(842_500L, fixture.gates().get("B2").accept(new RevenueVisitor()));
    }

    @Test void inventoryAccumulatesAcrossBothGates() {
        var result = fixture.airport().accept(new InventoryVisitor());
        assertEquals(9, result.leafCount());
        assertEquals(Map.of("Flight", 2, "Lounge", 2, "Shop", 2, "ParkingStand", 2,
                "ChargingStation", 1), result.counts());
    }

    @Test void inventorySnapshotsDoNotChangeAfterMoreVisits() {
        InventoryVisitor visitor = new InventoryVisitor();
        var first = fixture.gates().get("A1").accept(visitor);
        fixture.gates().get("B2").accept(visitor);
        assertEquals(5, first.leafCount());
        assertEquals(1, first.counts().get("Flight"));
        assertThrows(UnsupportedOperationException.class, () -> first.counts().clear());
    }

    @Test void filterReturnsOnlyBusyFlight() {
        var result = fixture.airport().accept(new FilterVisitor(
                e -> e instanceof Flight f && f.passengers() >= 100));
        assertEquals(List.of("KC101"), result.stream().map(Element::id).toList());
    }

    @Test void filterCanReturnContainersAndPreservesDepthFirstOrder() {
        var result = fixture.airport().accept(new FilterVisitor(e -> e instanceof Container));
        assertEquals(List.of("ALA", "T1", "A1", "B2"), result.stream().map(Element::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test void emptyContainerHasZeroRevenueAndEmptyInventory() {
        Gate empty = new Gate("E", "Empty", List.of());
        assertEquals(0L, empty.accept(new RevenueVisitor()));
        assertEquals(0, empty.accept(new InventoryVisitor()).leafCount());
        assertTrue(empty.accept(new FilterVisitor(e -> false)).isEmpty());
    }

    @Test void jsonNestsAirportTerminalGateAndLeavesExactly() {
        Airport airport = new Airport("A", "Airport", List.of(
                new Terminal("T", "Terminal", List.of(
                        new Gate("G", "Gate", List.of(new Flight("F", "Flight", 2, 300)))))));
        assertEquals("{\"type\":\"Airport\",\"id\":\"A\",\"name\":\"Airport\",\"children\":["
                + "{\"type\":\"Terminal\",\"id\":\"T\",\"name\":\"Terminal\",\"children\":["
                + "{\"type\":\"Gate\",\"id\":\"G\",\"name\":\"Gate\",\"children\":["
                + "{\"type\":\"Flight\",\"id\":\"F\",\"name\":\"Flight\","
                + "\"passengers\":2,\"revenueCents\":300}]}]}]}", airport.accept(new JsonVisitor()));
    }

    @Test void jsonEscapesQuotesBackslashesControlsAndUnicode() {
        Shop shop = new Shop("S", "A\"\\\n\r\t\b\f\u0001Қ😀", 10);
        assertEquals("{\"type\":\"Shop\",\"id\":\"S\","
                + "\"name\":\"A\\\"\\\\\\n\\r\\t\\b\\f\\u0001Қ\\ud83d\\ude00\","
                + "\"dailyRevenueCents\":10}", shop.accept(new JsonVisitor()));
    }

    @Test void jsonExportsAllBaseLeafTypes() {
        String json = fixture.airport().accept(new JsonVisitor());
        assertTrue(json.contains("\"type\":\"Flight\""));
        assertTrue(json.contains("\"type\":\"Lounge\""));
        assertTrue(json.contains("\"type\":\"Shop\""));
        assertTrue(json.contains("\"type\":\"ParkingStand\""));
        assertTrue(json.contains("\"type\":\"ChargingStation\""));
        assertEquals("{\"type\":\"Gate\",\"id\":\"E\",\"name\":\"Empty\",\"children\":[]}",
                new Gate("E", "Empty", List.of()).accept(new JsonVisitor()));
    }

    @Test void treeDefensivelyCopiesChildren() {
        List<Element> children = new ArrayList<>();
        children.add(new Shop("S", "Shop", 10));
        Gate gate = new Gate("G", "Gate", children);
        children.clear();
        assertEquals(1, gate.children().size());
        assertThrows(UnsupportedOperationException.class, () -> gate.children().clear());
    }

    @Test void invalidModelValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Flight("F", "Flight", -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new Shop("S", "Shop", -1));
        assertThrows(IllegalArgumentException.class, () -> new Gate("", "Gate", List.of()));
    }

    @Test void doubleDispatchWorksThroughElementReference() {
        Element leaf = new Lounge("L", "Lounge", 3, 2_500);
        assertEquals(7_500L, leaf.accept(new RevenueVisitor()));
        assertEquals("{\"type\":\"Lounge\",\"id\":\"L\",\"name\":\"Lounge\","
                + "\"visitors\":3,\"feeCents\":2500}", leaf.accept(new JsonVisitor()));
    }

    @Test void moneyOverflowIsReported() {
        Lounge lounge = new Lounge("L", "Lounge", 2, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> lounge.accept(new RevenueVisitor()));
        Gate gate = new Gate("G", "Gate", List.of(new Shop("S1", "One", Long.MAX_VALUE),
                new Shop("S2", "Two", 1)));
        assertThrows(ArithmeticException.class, () -> gate.accept(new RevenueVisitor()));
    }

    @Test void newChargingStationSupportsEveryOperation() {
        Element station = new ChargingStation("C", "Charging", 3, 500);
        assertEquals(1_500L, station.accept(new RevenueVisitor()));
        assertEquals(Map.of("ChargingStation", 1), station.accept(new InventoryVisitor()).counts());
        assertEquals(List.of(station), station.accept(new FilterVisitor(e -> e instanceof ChargingStation)));
        assertEquals(0, station.accept(new PassengerLoadVisitor()));
        assertEquals("{\"type\":\"ChargingStation\",\"id\":\"C\",\"name\":\"Charging\","
                + "\"sessions\":3,\"feeCents\":500}", station.accept(new JsonVisitor()));
        assertThrows(IllegalArgumentException.class,
                () -> new ChargingStation("C", "Charging", -1, 500));
    }
}
