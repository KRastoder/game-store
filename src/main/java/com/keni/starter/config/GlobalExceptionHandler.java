package com.keni.starter.config;

import java.net.URI;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns every failure into an RFC 9457 problem document, so clients get the same shape
 * whether the error came from bean validation, a service, or the security filter chain.
 *
 * <p>Without this, Spring's default error body includes the exception class, the message
 * and a full stack trace. On this API a failed validation was returning the entire
 * internal call stack to the caller, which names every class, line number and internal
 * package in the project.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Builds the document. ProblemDetail only fills in "type" when the status is an HTTP
   * error with no default URI, so it is set explicitly to keep the shape consistent
   * across every handler.
   */
  private static ProblemDetail problem(HttpStatusCode status, String title, String detail) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(URI.create("about:blank"));
    return problem;
  }

  /** Services signal expected outcomes by throwing ResponseStatusException. */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
    var status = HttpStatusCode.valueOf(ex.getStatusCode().value());
    var detail = ex.getBody().getDetail();

    // the caller is logged in and did something invalid, so this is not exceptional
    if (status.is5xxServerError()) {
      log.error("Server failure on a request the client could not have avoided", ex);
    } else {
      log.debug("Rejected request: {}", detail);
    }

    // HttpStatusCode.valueOf(...).toString() would render "404 NOT_FOUND", so the reason
    // phrase is resolved from HttpStatus to keep titles consistent with the other handlers
    var reason = HttpStatus.valueOf(status.value()).getReasonPhrase();
    var problem = problem(status, reason, detail);
    return ResponseEntity.status(status).body(problem);
  }

  /** @Valid failures. One entry per offending field so a client can highlight them. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    var fieldErrors = new LinkedHashMap<String, String>();
    for (var error : ex.getBindingResult().getFieldErrors()) {
      // first message per field wins, later constraints on the same field add nothing
      fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }

    var problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
        "One or more fields are invalid.");
    problem.setProperty("errors", fieldErrors);
    return ResponseEntity.badRequest().body(problem);
  }

  /**
   * Unparseable JSON, or an unknown field. Deliberately does not echo the offending
   * value back, since a client may have sent a password.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
    // deliberately generic: the rejected body may contain a password
    var problem = problem(HttpStatus.BAD_REQUEST, "Malformed request",
        "Request body is missing or malformed.");
    return ResponseEntity.badRequest().body(problem);
  }

  /**
   * Reached only when a security rule is thrown rather than denied at the filter. The
   * filter chain handles most 401s and 403s before a controller runs.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    var problem = problem(HttpStatus.FORBIDDEN, "Forbidden",
        "You do not have permission to do that.");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  /**
   * Last resort. Anything not handled above becomes a 500 with a generic body, because
   * the real exception is for the log, not the caller.
   *
   * <p>Framework exceptions that already carry a sensible status, such as 405 for a
   * wrong HTTP method or 415 for a bad content type, keep it. Without this they would
   * all be flattened into 500 and the API would lie about what went wrong.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleEverythingElse(Exception ex) {
    if (ex instanceof ErrorResponse errorResponse) {
      var status = errorResponse.getStatusCode();
      var detail = errorResponse.getBody().getDetail();
      log.debug("Rejected request: {}", detail);

      var problem = problem(status, HttpStatus.valueOf(status.value()).getReasonPhrase(),
          detail == null ? status.toString() : detail);
      return ResponseEntity.status(status).body(problem);
    }

    log.error("Unhandled exception", ex);

    var problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
        "Something went wrong on our side.");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }

  }