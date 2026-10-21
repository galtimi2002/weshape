package com.weshape.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One JSON error shape for the whole API.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} on purpose. Spring already knows the right
 * status for its own exceptions — 405 for the wrong method, 415 for the wrong content type, 400 for
 * unparseable JSON — and a bare {@code @ExceptionHandler(Exception.class)} would swallow all of
 * them into a 500. This class keeps those statuses and only re-skins the body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // ------------------------------------------------ application errors ---

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(
      NotFoundException e, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, e.getMessage(), request.getRequestURI(), null);
  }

  /** Rules that validation annotations cannot express: cross-field, or needing a lookup. */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Map<String, Object>> handleRuleViolation(
      ValidationException e, HttpServletRequest request) {
    return build(
        HttpStatus.BAD_REQUEST, e.getMessage(), request.getRequestURI(), e.getFieldErrors());
  }

  /**
   * Two users saved the same row at once. The version the client held no longer matches the
   * database, so the write is refused rather than allowed to silently overwrite the other person's
   * change. The client should reload and re-apply. 409 Conflict.
   */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, Object>> handleOptimisticLock(
      OptimisticLockingFailureException e, HttpServletRequest request) {
    log.debug("Optimistic lock conflict on {}: {}", request.getRequestURI(), e.getMessage());
    String message =
        "Someone else changed this item while you were editing. Reload and try again.";
    return build(HttpStatus.CONFLICT, message, request.getRequestURI(), null);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(
      DataIntegrityViolationException e, HttpServletRequest request) {
    log.debug("Data integrity violation on {}: {}", request.getRequestURI(), e.getMessage());
    String message = "That value is already taken";
    return build(HttpStatus.CONFLICT, message, request.getRequestURI(), null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(
      AccessDeniedException e, HttpServletRequest request) {
    String message =
        "You do not have permission to do that";
    return build(HttpStatus.FORBIDDEN, message, request.getRequestURI(), null);
  }

  /** Genuinely unexpected. Logged in full, reported vaguely — stack traces are not for clients. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnexpected(
      Exception e, HttpServletRequest request) {
    log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
    String message = "Something went wrong";
    return build(HttpStatus.INTERNAL_SERVER_ERROR, message, request.getRequestURI(), null);
  }

  // ------------------------------------------------- framework errors ---

  /** Bean validation failures, reshaped into the same body with per-field messages. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> fieldErrors =
        e.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    f -> f.getField(),
                    f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                    (a, b) -> a));

    return new ResponseEntity<>(
        body(HttpStatus.BAD_REQUEST, "Validation failed", pathOf(request), fieldErrors),
        headers,
        HttpStatus.BAD_REQUEST);
  }

  /**
   * Every other exception Spring MVC raises — 405, 415, 400 on unreadable bodies, 404 for unmapped
   * paths — keeps its status and gets our body.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception e,
      Object requestBody,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    String message = resolved == null ? "Request failed" : resolved.getReasonPhrase();
    return new ResponseEntity<>(body(status, message, pathOf(request), null), headers, status);
  }

  // ------------------------------------------------------------ shared ---


  private ResponseEntity<Map<String, Object>> build(
      HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
    return ResponseEntity.status(status).body(body(status, message, path, fieldErrors));
  }

  private Map<String, Object> body(
      HttpStatusCode status, String message, String path, Map<String, String> fieldErrors) {
    HttpStatus resolved = HttpStatus.resolve(status.value());

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status.value());
    body.put("error", resolved == null ? "Error" : resolved.getReasonPhrase());
    body.put("message", message);
    body.put("path", path);
    if (fieldErrors != null && !fieldErrors.isEmpty()) {
      body.put("fieldErrors", fieldErrors);
    }
    return body;
  }

  private String pathOf(WebRequest request) {
    if (request instanceof ServletWebRequest servletRequest) {
      return servletRequest.getRequest().getRequestURI();
    }
    return request.getDescription(false);
  }
}
