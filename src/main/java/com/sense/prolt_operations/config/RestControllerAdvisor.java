package com.sense.prolt_operations.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.sense.prolt_operations.exception.AppBaseException;
import com.sense.prolt_operations.exception.ErrorMessage;
import com.sense.prolt_operations.exception.ErrorResponse;

@RestControllerAdvice
public class RestControllerAdvisor extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestControllerAdvisor.class);

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        String requestURI = ((ServletWebRequest) webRequest).getRequest().getRequestURI();
        log.error("Method Not Allowed: [{}] {}", ex.getMethod(), requestURI, ex);
        return ResponseEntity.status(status).body(new ErrorResponse("Method Not Allowed"));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        String message = String.format("Parameter '%s' is required.", ex.getParameterName());
        log.error("Missing parameter: {}", ex.getMessage());
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        String message = Optional.ofNullable(ex.getFieldError())
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("Validation failed");
        log.error("Validation error: {}", message);
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        log.error("Malformed JSON request body: {}", ex.getMessage());
        return ResponseEntity.status(status).body(new ErrorResponse("Invalid request body. Please provide a valid JSON payload."));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest webRequest) {
        log.error("Max upload size exceeded: {}", ex.getMessage());
        String uri = ((ServletWebRequest) webRequest).getRequest().getRequestURI();
        String message = "File too large";
        if (uri != null && uri.startsWith("/api/uploads")) {
            message = "File size exceeds the 50MB limit.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Argument '%s' is invalid.", ex.getName());
        log.error("Argument type mismatch: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.error("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Unauthorized"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("Forbidden: Access denied"));
    }

    @ExceptionHandler(AppBaseException.class)
    public ResponseEntity<ErrorResponse> handleAppBaseException(AppBaseException ex) {
        ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = ex.getStatus() != null ? ex.getStatus()
                : (responseStatus != null ? responseStatus.code() : HttpStatus.INTERNAL_SERVER_ERROR);
        log.warn("Operational business exception [status: {}]: {}", status, ex.getMessage());
        return ResponseEntity.status(status).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        String msg = getRootMessage(ex);
        if (msg.contains("duplicate key") || msg.contains("unique constraint") || msg.contains("uq_") || msg.contains("23505")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Record already exists (Duplicate Conflict)"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Data integrity violation: " + ex.getMessage()));
    }

    private String getRootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage().toLowerCase()).append(" ");
            }
            t = t.getCause();
        }
        return sb.toString();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllUncaughtException(Exception ex) {
        log.error("Unhandled infrastructure exception 💥", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ErrorMessage.SOMETHING_WENT_WRONG.getMessage()));
    }
}
