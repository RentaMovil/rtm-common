package com.rentamovil.common.exception;

// Missing, invalid or expired token. Always maps to 401.
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
