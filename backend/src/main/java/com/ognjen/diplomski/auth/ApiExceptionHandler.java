package com.ognjen.diplomski.auth;

import com.ognjen.diplomski.exception.EmailAlreadyExistsException;
import com.ognjen.diplomski.exception.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validationFailed(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Podaci nisu ispravni.");
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(
                error.getField(), error.getDefaultMessage() == null ? "Podaci nisu ispravni." : error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), 400, "Bad Request",
                "VALIDATION_FAILED", message, request.getRequestURI(), fields));
    }

    @ExceptionHandler({InvalidRequestException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> badRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof HttpMessageNotReadableException
                ? "Request body is missing or malformed"
                : exception.getMessage();
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST,
                exception instanceof HttpMessageNotReadableException ? "MALFORMED_BODY" : "INVALID_REQUEST",
                message, request.getRequestURI()));
    }

    @ExceptionHandler({TypeMismatchException.class, ServletRequestBindingException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiError> invalidParameter(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Request parameters are missing or invalid", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Access is denied", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthorized(AuthenticationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ApiError> conflict(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "An account with this email already exists", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        // These reasons are deliberate public service messages; never serialize the cause.
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return response(status, message, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            return response(status, status.getReasonPhrase(), request);
        }
        log.error("Unhandled API failure: {} {}", request.getMethod(), request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred", request);
    }

    private static ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        String code = switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case INTERNAL_SERVER_ERROR -> "INTERNAL_ERROR";
            default -> status.name();
        };
        return ResponseEntity.status(status).body(ApiError.of(status, code, message, request.getRequestURI()));
    }
}
