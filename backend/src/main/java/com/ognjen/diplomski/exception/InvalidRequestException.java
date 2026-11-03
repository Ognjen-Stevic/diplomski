package com.ognjen.diplomski.exception;

/** A deliberate client-input rejection, distinct from programming errors. */
public class InvalidRequestException extends IllegalArgumentException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
