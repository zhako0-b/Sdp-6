package airport.observer;

import airport.events.Events.Event;
import airport.events.Events.FlightChanged;
import airport.events.Events.FlightStatus;

public final class FlightStatusSource extends EventSource<Event> {
    public void change(String gateId, String flightId, FlightStatus status) {
        publish(new FlightChanged(gateId, flightId, status));
    }
}
