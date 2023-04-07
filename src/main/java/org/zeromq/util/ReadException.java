package main.java.org.zeromq.util;

import java.util.concurrent.atomic.AtomicInteger;

public class ReadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public final int currentLineNumber;
    public final String currentLine;

    public ReadException(String message, String currentLine, AtomicInteger currentLineNumber) {
        super(String.format("%s %s: %s", message, currentLineNumber, currentLine));
        this.currentLine = currentLine;
        this.currentLineNumber = currentLineNumber.get();
    }
}