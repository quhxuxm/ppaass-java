package com.ppaass.common.exception;

public class PpaassException extends RuntimeException {
    public PpaassException() {
    }

    public PpaassException(String message) {
        super(message);
    }

    public PpaassException(String message, Throwable cause) {
        super(message, cause);
    }

    public PpaassException(Throwable cause) {
        super(cause);
    }

    public PpaassException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
