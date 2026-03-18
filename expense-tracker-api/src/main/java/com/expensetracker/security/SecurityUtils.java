package com.expensetracker.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Static utility class for extracting security information
 * from the Spring SecurityContext and TenantContext.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Extracts the current authenticated user's ID from the SecurityContext.
     * The user ID is stored as the principal (a String representation of UUID)
     * by the JwtAuthenticationFilter.
     *
     * @return the current user's UUID
     * @throws IllegalStateException if no authentication is present
     */
    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("No authentication context available");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }

    /**
     * Returns the current tenant ID from the TenantContext ThreadLocal.
     *
     * @return the current tenant's UUID
     * @throws IllegalStateException if no tenant context is set
     */
    public static UUID getCurrentTenantId() {
        return TenantContext.getCurrentTenant();
    }

    /**
     * Extracts the current user's role from the SecurityContext authorities.
     * Expects a single authority in the format "ROLE_ADMIN", "ROLE_MANAGER", or "ROLE_EMPLOYEE".
     * Returns the role name without the "ROLE_" prefix.
     *
     * @return the role name (e.g., "ADMIN", "MANAGER", "EMPLOYEE")
     * @throws IllegalStateException if no authentication or no authorities
     */
    public static String getCurrentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null
                || authentication.getAuthorities().isEmpty()) {
            throw new IllegalStateException("No authentication context or authorities available");
        }
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                .orElseThrow(() -> new IllegalStateException("No authority found"));
    }

    /**
     * Checks if the current user has the ADMIN role.
     *
     * @return true if the current user is an admin
     */
    public static boolean isAdmin() {
        return "ADMIN".equals(getCurrentRole());
    }

    /**
     * Checks if the current user has the MANAGER role.
     *
     * @return true if the current user is a manager
     */
    public static boolean isManager() {
        return "MANAGER".equals(getCurrentRole());
    }
}
