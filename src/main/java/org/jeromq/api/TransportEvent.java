package org.jeromq.api;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public enum TransportEvent {
    ZMQ_EVENT_CONNECTED(1),
    ZMQ_EVENT_CONNECT_DELAYED(2),
    ZMQ_EVENT_CONNECT_RETRIED(4),
    ZMQ_EVENT_CONNECT_FAILED(1024),

    ZMQ_EVENT_LISTENING(8),
    ZMQ_EVENT_BIND_FAILED(16),

    ZMQ_EVENT_ACCEPTED(32),
    ZMQ_EVENT_ACCEPT_FAILED(64),

    ZMQ_EVENT_CLOSED(128),
    ZMQ_EVENT_CLOSE_FAILED(256),
    ZMQ_EVENT_DISCONNECTED(512);

    public static final EnumSet<TransportEvent> ALL = EnumSet.of(ZMQ_EVENT_CONNECTED, ZMQ_EVENT_CONNECT_DELAYED,
            ZMQ_EVENT_CONNECT_RETRIED, ZMQ_EVENT_LISTENING,
            ZMQ_EVENT_BIND_FAILED, ZMQ_EVENT_ACCEPTED,
            ZMQ_EVENT_ACCEPT_FAILED, ZMQ_EVENT_CLOSED,
            ZMQ_EVENT_CLOSE_FAILED, ZMQ_EVENT_DISCONNECTED);

    private final int cValue;

    TransportEvent(int cValue) {
        this.cValue = cValue;
    }

    public int getCValue() {
        return cValue;
    }

    static int getCValue(EnumSet<TransportEvent> events) {
        int result = 0;
        for (TransportEvent event : events) {
            result |= event.cValue;
        }
        return result;
    }

    static EnumSet<TransportEvent> decode(int cValue) {
        if (cValue == 0) {
            return EnumSet.noneOf(TransportEvent.class);
        }
        List<TransportEvent> values = new ArrayList<TransportEvent>();

        for (TransportEvent event : values()) {
            if ((cValue | event.getCValue()) == cValue) {
                values.add(event);
                cValue -= event.getCValue();
            }
        }

        if (cValue > 0) {
            throw new IllegalArgumentException("Not a valid cValue for TransportEvents");
        }

        return EnumSet.copyOf(values);
    }
}
