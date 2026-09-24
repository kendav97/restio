package com.restio.shared.tenant;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Clears the restaurant context when the request ends, so a pooled thread never inherits the
 * restaurants of the previous caller. Filling the context from the JWT arrives in stage 0.3.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestaurantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            RestaurantContext.clear();
        }
    }
}
