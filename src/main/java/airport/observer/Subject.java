package airport.observer;

import airport.events.Events.Event;
import airport.events.Events.Type;

public interface Subject<E extends Event> {
    Subscription subscribe(Type type, Observer<? super E> observer);

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override void close();
    }
}
