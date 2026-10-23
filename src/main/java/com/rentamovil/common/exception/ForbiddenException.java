package com.rentamovil.common.exception;

// The caller lacks the required permission. Maps to 403.
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("You don't have permission to perform this action");
    }

    public ForbiddenException(String message) {
        super(message);
    }
}
