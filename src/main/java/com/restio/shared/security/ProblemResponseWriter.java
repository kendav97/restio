package com.restio.shared.security;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restio.shared.error.ErrorCode;

/**
 * Writes the 401 and 403 answers of the security filter chain, which run before any controller and
 * so never reach the global exception handler, in the same problem format.
 */
@Component
public class ProblemResponseWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public ProblemResponseWriter(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.Common.UNAUTHORIZED,
                "error.unauthorized");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ErrorCode.Common.FORBIDDEN,
                "error.forbidden");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode code,
            String messageKey)
            throws IOException {
        String message =
                messageSource.getMessage(messageKey, null, messageKey, request.getLocale());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setType(
                URI.create(
                        "https://restio.app/errors/"
                                + code.code().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(message);
        problem.setProperty("code", code.code());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
