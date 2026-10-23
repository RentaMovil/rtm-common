package com.rentamovil.common.exception;

import lombok.Getter;

// Trying to create something that already exists (e.g. a repeated plate). Maps to 409.
@Getter
public class ResourceAlreadyExistsException extends RuntimeException {

    // Error code from the contract (e.g. PLATE_ALREADY_EXISTS)
    private final String code;

    public ResourceAlreadyExistsException(String code, String message) {
        super(message);
        this.code = code;
    }
}
