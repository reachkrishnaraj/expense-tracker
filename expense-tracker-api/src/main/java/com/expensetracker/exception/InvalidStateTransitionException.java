package com.expensetracker.exception;

public class InvalidStateTransitionException extends RuntimeException {

    private final String fromStatus;
    private final String toStatus;

    public InvalidStateTransitionException(String fromStatus, String toStatus) {
        super("Invalid state transition from " + fromStatus + " to " + toStatus);
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }
}
