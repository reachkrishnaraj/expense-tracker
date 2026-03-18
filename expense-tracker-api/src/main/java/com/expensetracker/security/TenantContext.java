package com.expensetracker.security;

import java.util.UUID;

/**
 * ThreadLocal-based holder for the current tenant context.
 * Set by the TenantContextFilter after JWT authentication,
 * and cleared in the filter's finally block.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utility class — prevent instantiation
    }

    public static void setCurrentTenant(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        UUID tenant = CURRENT_TENANT.get();
        if (tenant == null) {
            throw new IllegalStateException("No tenant context set");
        }
        return tenant;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
