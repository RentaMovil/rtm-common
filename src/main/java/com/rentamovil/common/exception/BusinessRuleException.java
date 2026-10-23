package com.rentamovil.common.exception;

import lombok.Getter;

// A business rule forbids the operation. Maps to 409.
@Getter
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }
}
