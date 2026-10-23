package com.rentamovil.common.web.exception;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.rentamovil.common.exception.BusinessRuleException;
import com.rentamovil.common.exception.ForbiddenException;
import com.rentamovil.common.exception.InvalidTokenException;
import com.rentamovil.common.exception.ResourceAlreadyExistsException;
import com.rentamovil.common.exception.ResourceNotFoundException;
import com.rentamovil.common.web.dto.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Generic exception-to-{@link ErrorResponse} mapping shared by every RentaMovil microservice.
 *
 * <p>A service's own {@code @RestControllerAdvice} class extends this one and adds
 * {@code @ExceptionHandler} methods only for the exceptions specific to its own domain.
 * {@code @RestControllerAdvice} is not inherited by subclasses in Spring, so the concrete class
 * must repeat that annotation for Spring to register it as advice.
 *
 * <p>Override {@link #mapIntegrityViolation(String)} to recognize this service's own DB
 * constraint names (e.g. {@code uq_vehicle_plate}); the default falls back to a generic
 * {@code CONFLICT}.
 */
@Slf4j
public abstract class BaseExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return new ErrorResponse("VALIDATION_ERROR", "Invalid input data", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadJson() {
        return new ErrorResponse("VALIDATION_ERROR", "The request body is not valid JSON");
    }

    // E.g. /vehicles/abc or dateFrom=tomorrow: the value can't be converted to the expected type
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return new ErrorResponse("VALIDATION_ERROR", "Invalid input data",
                List.of(new ErrorResponse.FieldError(e.getName(), "Invalid format")));
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAlreadyExists(ResourceAlreadyExistsException e) {
        return new ErrorResponse(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleBusinessRule(BusinessRuleException e) {
        return new ErrorResponse(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenException e) {
        return new ErrorResponse("FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException e) {
        return new ErrorResponse("NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidToken(InvalidTokenException e) {
        return new ErrorResponse("INVALID_TOKEN", e.getMessage());
    }

    // What the database stops even though the use case already checked it (e.g. two inserts
    // racing on the same unique key). mapIntegrityViolation lets each service recognize its own
    // constraint names; anything it doesn't recognize falls back to a generic CONFLICT here.
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIntegrity(DataIntegrityViolationException e) {
        String cause = String.valueOf(e.getMostSpecificCause().getMessage());
        ErrorResponse mapped = mapIntegrityViolation(cause);
        if (mapped != null) {
            return mapped;
        }
        log.error("Unexpected integrity violation", e);
        return new ErrorResponse("CONFLICT", "The data conflicts with an existing record");
    }

    /**
     * Recognizes this service's own DB constraint names in a {@link DataIntegrityViolationException}
     * cause message (e.g. {@code uq_vehicle_plate}). Return {@code null} to fall back to the
     * generic {@code CONFLICT} response.
     */
    protected ErrorResponse mapIntegrityViolation(String cause) {
        return null;
    }

    // Spring's own errors that already carry an HTTP status: missing parameter (400), method not
    // allowed (405), unknown path (404), unsupported Content-Type (415)... That status is kept.
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleSpringWebError(ErrorResponseException e) {
        return springWebError(e.getStatusCode());
    }

    // The detail only goes to the log, never into the response
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // MissingServletRequestParameterException and similar implement Spring's ErrorResponse interface
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            return springWebError(springError.getStatusCode());
        }
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private ResponseEntity<ErrorResponse> springWebError(HttpStatusCode status) {
        String code = switch (status.value()) {
            case 400 -> "VALIDATION_ERROR";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "REQUEST_ERROR";
        };
        String message = status.value() == 400
                ? "Required parameters are missing or have an invalid format"
                : "The request is not valid for this path";
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
