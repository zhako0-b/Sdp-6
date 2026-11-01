package airport.observer;

import airport.events.Events.Event;
import airport.events.Events.GateChanged;

public final class GateStatusSource extends EventSource<Event> {
    public void change(String gateId, boolean open) {
        publish(new GateChanged(gateId, open));
    }
}
