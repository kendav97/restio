package com.restio.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception that escapes a controller into an RFC 7807 problem response with a stable
 * {@code code}. Internal failures are logged but never leak their stack trace to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://restio.app/errors/";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex, Locale locale) {
        return problem(
                HttpStatus.NOT_FOUND,
                ex.errorCode(),
                message("error.notFound", locale),
                ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException ex, Locale locale) {
        return problem(
                HttpStatus.CONFLICT,
                ex.errorCode(),
                message("error.businessRule", locale),
                ex.getMessage());
    }

    @ExceptionHandler({
        OptimisticLockException.class,
        ObjectOptimisticLockingFailureException.class
    })
    ProblemDetail handleOptimisticLock(Exception ex, Locale locale) {
        return problem(
                HttpStatus.CONFLICT,
                ErrorCode.Common.CONCURRENT_MODIFICATION,
                message("error.concurrentModification", locale),
                message("error.concurrentModification.detail", locale));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex, Locale locale) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.Common.VALIDATION_FAILED,
                        message("error.validation", locale),
                        message("error.validation", locale));
        problem.setProperty(
                "errors",
                ex.getConstraintViolations().stream()
                        .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                        .toList());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, Locale locale) {
        return problem(
                HttpStatus.FORBIDDEN,
                ErrorCode.Common.FORBIDDEN,
                message("error.forbidden", locale),
                message("error.forbidden", locale));
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail handleUnauthorized(UnauthorizedException ex, Locale locale) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                ex.errorCode(),
                message("error.unauthorized", locale),
                ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex, Locale locale) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.Common.UNAUTHORIZED,
                message("error.unauthorized", locale),
                message("error.unauthorized", locale));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, Locale locale) {
        log.error("Unhandled exception", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.Common.INTERNAL_ERROR,
                message("error.internal", locale),
                message("error.internal", locale));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Locale locale = request.getLocale();
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.Common.VALIDATION_FAILED,
                        message("error.validation", locale),
                        message("error.validation", locale));

        List<FieldError> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                        .toList();
        problem.setProperty("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    private ProblemDetail problem(
            HttpStatus status, ErrorCode errorCode, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + slug(errorCode)));
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setProperty("code", errorCode.code());
        return problem;
    }

    private static String slug(ErrorCode errorCode) {
        return errorCode.code().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
    }
}
