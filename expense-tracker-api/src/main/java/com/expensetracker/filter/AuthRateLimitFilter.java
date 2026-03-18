package com.expensetracker.filter;

import com.expensetracker.ratelimit.InMemoryRateLimiter;
import com.expensetracker.ratelimit.RateLimitResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_RATE_LIMIT_CAPACITY = 20;
    private static final double AUTH_REFILL_RATE_PER_SECOND = 20.0 / 60.0; // 20 per minute

    private final InMemoryRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(InMemoryRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        String key = "auth:" + clientIp;

        RateLimitResult result = rateLimiter.tryConsume(key, AUTH_RATE_LIMIT_CAPACITY,
                AUTH_REFILL_RATE_PER_SECOND);

        // Add rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetTimestamp()));

        if (!result.isAllowed()) {
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "Rate limit exceeded. Retry after " + result.getRetryAfterSeconds() + " seconds.");
            errorBody.put("code", "RATE_LIMIT_EXCEEDED");
            errorBody.put("timestamp", Instant.now().toString());
            errorBody.put("path", request.getRequestURI());

            objectMapper.writeValue(response.getWriter(), errorBody);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
