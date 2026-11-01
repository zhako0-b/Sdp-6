package airport.observer;

import airport.events.Events.Event;
import airport.events.Events.Type;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Synchronous push delivery on the publishing thread.
 * Every publication captures one immutable subscription snapshot.
 */
public class EventSource<E extends Event> implements Subject<E> {
    private final Map<Type, CopyOnWriteArrayList<Registration<E>>> listeners;

    public EventSource() {
        EnumMap<Type, CopyOnWriteArrayList<Registration<E>>> registry =
                new EnumMap<>(Type.class);
        for (Type type : Type.values()) registry.put(type, new CopyOnWriteArrayList<>());
        listeners = Map.copyOf(registry);
    }

    @Override
    public Subscription subscribe(Type type, Observer<? super E> observer) {
        Objects.requireNonNull(type);
        Registration<E> registration = new Registration<>(Objects.requireNonNull(observer));
        CopyOnWriteArrayList<Registration<E>> bucket = listeners.get(type);
        bucket.add(registration);
        return () -> bucket.remove(registration);
    }

    public void publish(E event) {
        Objects.requireNonNull(event);
        // The COW iterator retains its original snapshot if callbacks change the list.
        DeliveryException failure = null;
        for (Registration<E> registration : listeners.get(event.type())) {
            try {
                registration.observer.onEvent(event);
            } catch (RuntimeException error) {
                if (failure == null) failure = new DeliveryException();
                failure.addSuppressed(error);
            }
        }
        // One broken callback does not skip the other subscribers.
        if (failure != null) throw failure;
    }

    private static final class Registration<E> {
        private final Observer<? super E> observer;
        private Registration(Observer<? super E> observer) { this.observer = observer; }
    }

    public static final class DeliveryException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private DeliveryException() { super("One or more listeners failed"); }
    }
}
