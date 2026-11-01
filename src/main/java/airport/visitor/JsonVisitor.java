package airport.visitor;

import airport.model.AirportTree.*;
import java.util.stream.Collectors;

/** Hand-written JSON, including all control-character escapes and tree nesting. */
public final class JsonVisitor implements Visitor<String> {
    @Override public String visit(Airport airport) { return container("Airport", airport); }
    @Override public String visit(Terminal terminal) { return container("Terminal", terminal); }
    @Override public String visit(Gate gate) { return container("Gate", gate); }
    @Override public String visit(Flight flight) {
        return header("Flight", flight) + ",\"passengers\":" + flight.passengers()
                + ",\"revenueCents\":" + flight.revenueCents() + "}";
    }
    @Override public String visit(Lounge lounge) {
        return header("Lounge", lounge) + ",\"visitors\":" + lounge.visitors()
                + ",\"feeCents\":" + lounge.feeCents() + "}";
    }
    @Override public String visit(Shop shop) {
        return header("Shop", shop) + ",\"dailyRevenueCents\":" + shop.dailyRevenueCents() + "}";
    }
    @Override public String visit(ParkingStand stand) {
        return header("ParkingStand", stand) + ",\"dailyFeeCents\":" + stand.dailyFeeCents() + "}";
    }
    @Override public String visit(ChargingStation station) {
        return header("ChargingStation", station) + ",\"sessions\":" + station.sessions()
                + ",\"feeCents\":" + station.feeCents() + "}";
    }

    private String container(String type, Container container) {
        String children = container.children().stream().map(child -> child.accept(this))
                .collect(Collectors.joining(","));
        return header(type, container) + ",\"children\":[" + children + "]}";
    }

    private String header(String type, Element element) {
        return "{\"type\":" + quote(type) + ",\"id\":" + quote(element.id())
                + ",\"name\":" + quote(element.name());
    }

    private String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20 || Character.isSurrogate(c))
                        result.append(String.format("\\u%04x", (int) c));
                    else result.append(c);
                }
            }
        }
        return result.append('"').toString();
    }
}
