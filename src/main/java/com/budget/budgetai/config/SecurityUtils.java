package com.budget.budgetai.config;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    /**
     * Extract the authenticated user's ID from the SecurityContext.
     * The user ID is stored as the credentials field of the authentication token
     * by JwtAuthenticationFilter.
     */
    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication instanceof UsernamePasswordAuthenticationToken)) {
            throw new AccessDeniedException("Not authenticated");
        }
        Object credentials = authentication.getCredentials();
        if (credentials instanceof UUID) {
            return (UUID) credentials;
        }
        throw new AccessDeniedException("Unable to extract user identity");
    }

    /**
     * Verify that the authenticated user matches the given userId.
     * Throws AccessDeniedException if they don't match.
     */
    public static void verifyOwnership(UUID resourceOwnerId) {
        UUID currentUserId = getCurrentUserId();
        if (!currentUserId.equals(resourceOwnerId)) {
            throw new AccessDeniedException("You do not have permission to access this resource");
        }
    }
}
