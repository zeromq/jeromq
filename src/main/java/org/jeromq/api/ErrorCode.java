package org.jeromq.api;

import zmq.ZError;

public enum ErrorCode {

    NOT_SUPPORTED(ZError.ENOTSUP),
    PROTOCOL_NOT_SUPPORTED(ZError.EPROTONOSUPPORT),
    NO_BUFFERS(ZError.ENOBUFS),
    NETWORK_DOWN(ZError.ENETDOWN),
    ADDRESS_IN_USE(ZError.EADDRINUSE),
    ADDRESS_NOT_AVAILABLE(ZError.EADDRNOTAVAIL),
    CONNECTION_REFUSED(ZError.ECONNREFUSED),
    IN_PROGRESS(ZError.EINPROGRESS),
    MISSING_THREAD(ZError.EMTHREAD),
    INAPPROPRIATE_STATE(ZError.EFSM),
    NO_COMPATIBLE_PROTOCOL(ZError.ENOCOMPATPROTO),
    HOST_UNREACHABLE(ZError.EHOSTUNREACH),
    TERMINATE(ZError.ETERM);

    private final long code;

    ErrorCode(long code) {
        this.code = code;
    }

    public long getCode() {
        return code;
    }

    public static ErrorCode findByCode(int code) {
        for (ErrorCode e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown " + ErrorCode.class.getName() + " enum code:" + code);
    }
}