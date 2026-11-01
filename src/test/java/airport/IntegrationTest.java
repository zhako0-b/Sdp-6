package airport;

import airport.concurrent.ProducerRunner;
import airport.events.Events.*;
import airport.listener.AirportListeners.*;
import airport.listener.InspectionService;
import airport.model.AirportTree.Gate;
import airport.observer.*;
import airport.visitor.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class IntegrationTest {
    private final DemoData.Fixture fixture = DemoData.create();
    private final EventSource<Event> reports = new EventSource<>();
    private final ReportCollector collector = new ReportCollector();

    private GateStatusSource connect(Map<String, Gate> gateIndex) {
        GateStatusSource source = new GateStatusSource();
        InspectionService inspection = new InspectionService(
                gateIndex, InventoryVisitor::new, new RevenueVisitor(), reports);
        source.subscribe(Type.GATE_CHANGED, inspection);
        reports.subscribe(Type.INSPECTION_REPORT, collector);
        return source;
    }

    @Test void closurePublishesReportForOnlyRelatedSubtree() {
        GateStatusSource source = connect(fixture.gates());
        source.change("A1", false);
        InspectionReport report = collector.reports().get(0);
        assertEquals("A1", report.gateId());
        assertEquals(5, report.leafCount());
        assertEquals(1_281_500L, report.revenueCents());
        assertEquals(1, report.inventory().get("Flight"));
        assertEquals(1, collector.reports().size());
    }

    @Test void openingGateDoesNotTriggerInspection() {
        GateStatusSource source = connect(fixture.gates());
        source.change("A1", true);
        assertTrue(collector.reports().isEmpty());
    }

    @Test void unknownClosedGateIsReportedAsFailure() {
        GateStatusSource source = connect(fixture.gates());
        var failure = assertThrows(EventSource.DeliveryException.class,
                () -> source.change("missing", false));
        assertInstanceOf(IllegalArgumentException.class, failure.getSuppressed()[0]);
        assertTrue(collector.reports().isEmpty());
    }

    @Test void simultaneousInspectionsUseIndependentStatefulVisitors() {
        GateStatusSource source = connect(fixture.gates());
        List<Runnable> jobs = IntStream.range(0, 4).mapToObj(index -> (Runnable) () -> {
            for (int i = 0; i < 100; i++) source.change(index % 2 == 0 ? "A1" : "B2", false);
        }).toList();
        try (ProducerRunner runner = new ProducerRunner(Executors.newFixedThreadPool(4))) {
            runner.run(jobs);
        }
        assertEquals(400, collector.reports().size());
        for (InspectionReport report : collector.reports()) {
            assertEquals(report.gateId().equals("A1") ? 5 : 4, report.leafCount());
            assertEquals(1, report.inventory().get("Flight"));
            assertEquals(report.gateId().equals("A1") ? 1_281_500L : 842_500L, report.revenueCents());
        }
    }

    @Test void airportListenersPerformTheirOwnResponsibilities() {
        FlightStatusSource flights = new FlightStatusSource();
        GateStatusSource gates = new GateStatusSource();
        DepartureBoard board = new DepartureBoard();
        PassengerSms sms = new PassengerSms();
        BaggageSystem baggage = new BaggageSystem();
        GateManager manager = new GateManager();
        AuditLog log = new AuditLog();
        flights.subscribe(Type.FLIGHT_CHANGED, board);
        flights.subscribe(Type.FLIGHT_CHANGED, sms);
        flights.subscribe(Type.FLIGHT_CHANGED, baggage);
        flights.subscribe(Type.FLIGHT_CHANGED, log);
        gates.subscribe(Type.GATE_CHANGED, manager);
        flights.change("A1", "KC101", FlightStatus.DELAYED);
        gates.change("A1", false);
        assertEquals(FlightStatus.DELAYED, board.snapshot().get("KC101"));
        assertEquals(List.of("KC101: DELAYED"), sms.messages());
        assertEquals(1, baggage.handledEvents());
        assertFalse(manager.snapshot().get("A1"));
        assertEquals(1, log.snapshot().size());
    }
}
