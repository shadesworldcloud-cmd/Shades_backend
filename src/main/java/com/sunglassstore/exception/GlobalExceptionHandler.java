package com.sunglassstore.exception;

import com.sunglassstore.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * A path variable that will not convert — {@code GET /api/products/best-sellers} against a
     * {@code Long productId} mapping, say. This used to fall through to the catch-all below and
     * return 500, which reads as "the server is broken" when the truth is "that is not a valid id".
     *
     * It is worth its own handler because it is exactly how a route-ordering mistake shows up: when
     * a literal path stops being matched ahead of its {@code /{id}} sibling, the symptom is this
     * exception. A 400 names the problem; a 500 sends whoever is debugging it looking at the
     * database.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String expected = ex.getRequiredType() == null ? "the expected type" : ex.getRequiredType().getSimpleName();
        return buildResponse(HttpStatus.BAD_REQUEST,
                "'" + ex.getValue() + "' is not a valid value for " + ex.getName() + " (expected " + expected + ")",
                request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * A stale write: the caller's expected version no longer matches.
     *
     * 409 rather than 400 because the request was not malformed — it was correct against data that
     * has since moved. The message tells the customer what to do about it, and the write is NOT
     * retried: replaying a value chosen against stale data is exactly the silent overwrite the
     * version check exists to prevent.
     *
     * ObjectOptimisticLockingFailureException is Hibernate's own version of this, raised for the
     * entities whose @Version it manages (Address). Both funnel to the same answer so a client sees
     * one behaviour whichever mechanism detected the conflict.
     */
    @ExceptionHandler({ OptimisticLockConflictException.class,
            org.springframework.orm.ObjectOptimisticLockingFailureException.class })
    public ResponseEntity<ErrorResponse> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        LOGGER.warn("Optimistic lock conflict on {} - {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return buildResponse(HttpStatus.CONFLICT,
                "This information was updated elsewhere. Refresh and review the latest version before trying again.",
                request);
    }

    /**
     * Transient database contention: a deadlock victim or a lock-wait timeout.
     *
     * Surfaced as 409 rather than 500 because it is a retryable state conflict, not a fault — the
     * request was valid and would likely succeed on its own. Nothing is retried automatically here:
     * these arrive from arbitrary write paths, and replaying one that is not idempotent could
     * duplicate an order or a payment. The message says the operation can be retried; the decision
     * stays with the caller.
     */
    @ExceptionHandler({ org.springframework.dao.CannotAcquireLockException.class,
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.QueryTimeoutException.class })
    public ResponseEntity<ErrorResponse> handleLockContention(Exception ex, HttpServletRequest request) {
        LOGGER.warn("Database contention on {} - {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return buildResponse(HttpStatus.CONFLICT,
                "The system was busy processing another change. Please try that again.", request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientInventory(InsufficientInventoryException ex,
                                                                      HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCouponException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCoupon(InvalidCouponException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Authentication failed", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "One or more fields have validation errors",
                request.getRequestURI()
        );
        errorResponse.setValidationErrors(errors);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Service-detected field errors, answered in exactly the shape bean-validation failures use —
     * status 400, message "Validation Failed", and a validationErrors map keyed by field path —
     * so a client has one rendering path for "price is negative" and "this SKU is already used by
     * variant 1".
     */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ErrorResponse> handleFieldValidation(FieldValidationException ex,
                                                                HttpServletRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                ex.getMessage(),
                request.getRequestURI()
        );
        errorResponse.setValidationErrors(ex.getFieldErrors());
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex,
                                                                  HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body or unsupported field value", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                              HttpServletRequest request) {
        String message = "Database constraint violation";
        if (ex.getMessage() != null && ex.getMessage().contains("Duplicate")) {
            message = "A record with this value already exists";
        }
        return buildResponse(HttpStatus.CONFLICT, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message,
                                                         HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(error);
    }
}
