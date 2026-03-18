package com.expensetracker.filter;

import com.expensetracker.ratelimit.InMemoryRateLimiter;
import com.expensetracker.ratelimit.RateLimitResult;
import com.expensetracker.security.TenantContext;
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
import java.util.UUID;

@Component
public class TenantRateLimitFilter extends OncePerRequestFilter {

    private static final int TENANT_RATE_LIMIT_CAPACITY = 100;
    private static final double TENANT_REFILL_RATE_PER_SECOND = 100.0 / 60.0; // 100 per minute

    private final InMemoryRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public TenantRateLimitFilter(InMemoryRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Only apply to non-auth /api/v1/** paths
        return path.startsWith("/api/v1/auth/") || !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        UUID tenantId;
        try {
            tenantId = TenantContext.getCurrentTenant();
        } catch (IllegalStateException e) {
            // No tenant context set yet (e.g., unauthenticated request) - skip rate limiting
            filterChain.doFilter(request, response);
            return;
        }

        String key = "tenant:" + tenantId;

        RateLimitResult result = rateLimiter.tryConsume(key, TENANT_RATE_LIMIT_CAPACITY,
                TENANT_REFILL_RATE_PER_SECOND);

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
}
