package airport;

import airport.concurrent.ProducerRunner;
import airport.events.Events.*;
import airport.listener.AirportListeners.*;
import airport.listener.InspectionService;
import airport.model.AirportTree.Flight;
import airport.observer.*;
import airport.visitor.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/** The only production composition root: constructs dependencies and connects them. */
public final class AirportApp {
    private AirportApp() { }

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? positive(args[0]) : 3;
        int eventsPerProducer = args.length > 1 ? positive(args[1]) : 20;
        DemoData.Fixture fixture = DemoData.create();
        FlightStatusSource flights = new FlightStatusSource();
        GateStatusSource gates = new GateStatusSource();
        EventSource<Event> reports = new EventSource<>();
        DepartureBoard board = new DepartureBoard();
        PassengerSms sms = new PassengerSms();
        GateManager gateManager = new GateManager();
        BaggageSystem baggage = new BaggageSystem();
        TrafficAggregator aggregator = new TrafficAggregator();
        AuditLog audit = new AuditLog();
        ReportCollector collector = new ReportCollector();
        RevenueVisitor revenue = new RevenueVisitor();
        InspectionService inspection = new InspectionService(
                fixture.gates(), InventoryVisitor::new, revenue, reports);

        flights.subscribe(Type.FLIGHT_CHANGED, board);
        flights.subscribe(Type.FLIGHT_CHANGED, sms);
        flights.subscribe(Type.FLIGHT_CHANGED, baggage);
        flights.subscribe(Type.FLIGHT_CHANGED, aggregator);
        flights.subscribe(Type.FLIGHT_CHANGED, audit);
        gates.subscribe(Type.GATE_CHANGED, gateManager);
        gates.subscribe(Type.GATE_CHANGED, inspection);
        gates.subscribe(Type.GATE_CHANGED, audit);
        aggregator.subscribe(Type.TRAFFIC_SUMMARY, audit);
        reports.subscribe(Type.INSPECTION_REPORT, collector);
        reports.subscribe(Type.INSPECTION_REPORT, audit);

        List<Runnable> producers = new ArrayList<>();
        for (int producer = 0; producer < 3; producer++) {
            int index = producer;
            producers.add(() -> {
                String gateId = index % 2 == 0 ? "A1" : "B2";
                String flightId = index % 2 == 0 ? "KC101" : "KC202";
                for (int i = 0; i < eventsPerProducer; i++)
                    flights.change(gateId, flightId,
                            i % 2 == 0 ? FlightStatus.BOARDING : FlightStatus.DELAYED);
                gates.change(gateId, false);
            });
        }

        try (ProducerRunner runner = new ProducerRunner(Executors.newFixedThreadPool(threads))) {
            runner.run(producers);
        }

        var inventory = fixture.airport().accept(new InventoryVisitor());
        var busyFlights = fixture.airport().accept(new FilterVisitor(
                element -> element instanceof Flight flight && flight.passengers() >= 100));
        String json = fixture.airport().accept(new JsonVisitor());
        Files.createDirectories(Path.of("output"));
        Files.writeString(Path.of("output", "airport.json"), json);

        System.out.println("Airport Operations - Observer + Visitor");
        System.out.println("Pool threads: " + threads + "; producer jobs: 3");
        System.out.println("Flight events: " + baggage.handledEvents());
        System.out.println("SMS outbox: " + sms.messages().size());
        System.out.println("Board flights: " + board.snapshot().size());
        System.out.println("Aggregated A1: " + aggregator.count("A1"));
        System.out.println("Aggregated B2: " + aggregator.count("B2"));
        System.out.println("Inspection reports: " + collector.reports().size());
        collector.reports().stream().sorted(java.util.Comparator.comparing(InspectionReport::gateId))
                .forEach(report -> System.out.println("  " + report.gateId()
                        + ": leaves=" + report.leafCount() + ", revenueCents=" + report.revenueCents()));
        System.out.println("Total revenue (cents): " + fixture.airport().accept(revenue));
        System.out.println("Passenger load: " + fixture.airport().accept(new PassengerLoadVisitor()));
        System.out.println("Inventory: " + new java.util.TreeMap<>(inventory.counts()));
        System.out.println("Busy flights: " + busyFlights.stream().map(Flight.class::cast)
                .map(Flight::id).toList());
        System.out.println("Audit events: " + audit.snapshot().size());
        System.out.println("JSON: output/airport.json");
        System.out.println("Executor terminated cleanly.");
    }

    private static int positive(String argument) {
        int value = Integer.parseInt(argument);
        if (value < 1) throw new IllegalArgumentException("Arguments must be positive integers");
        return value;
    }
}
