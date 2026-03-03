package com.budget.budgetai.service;

import com.budget.budgetai.model.AppUser;
import com.budget.budgetai.model.RefreshToken;
import com.budget.budgetai.repository.AppUserRepository;
import com.budget.budgetai.repository.RefreshTokenRepository;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AppUserRepository appUserRepository;

    private JwtService jwtService;

    private UUID userId;
    private AppUser appUser;

    // 256-bit key (at least 32 bytes for HS256)
    private static final String TEST_SECRET = "test-secret-key-that-is-at-least-32-bytes-long-for-hs256";
    private static final long ACCESS_TOKEN_EXPIRATION = 900000L; // 15 min
    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L; // 7 days

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, ACCESS_TOKEN_EXPIRATION, REFRESH_TOKEN_EXPIRATION,
                refreshTokenRepository, appUserRepository);

        userId = UUID.randomUUID();
        appUser = new AppUser();
        appUser.setId(userId);
        appUser.setEmail("test@example.com");
        appUser.setPasswordHash("hashedPassword");
    }

    // --- generateAccessToken ---

    @Test
    void generateAccessToken_returnsNonNullToken() {
        String token = jwtService.generateAccessToken(userId, "test@example.com");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateAccessToken_containsCorrectClaims() {
        String token = jwtService.generateAccessToken(userId, "test@example.com");

        UUID extractedUserId = jwtService.extractUserId(token);
        String extractedEmail = jwtService.extractEmail(token);

        assertEquals(userId, extractedUserId);
        assertEquals("test@example.com", extractedEmail);
    }

    // --- generateRefreshToken ---

    @Test
    void generateRefreshToken_savesTokenToDatabase() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String token = jwtService.generateRefreshToken(userId);

        assertNotNull(token);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken savedToken = captor.getValue();
        assertEquals(appUser, savedToken.getAppUser());
        assertEquals(token, savedToken.getToken());
        assertNotNull(savedToken.getExpiresAt());
    }

    @Test
    void generateRefreshToken_nonExistentUser_throwsEntityNotFoundException() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> jwtService.generateRefreshToken(userId));
    }

    // --- extractUserId ---

    @Test
    void extractUserId_validToken_returnsUserId() {
        String token = jwtService.generateAccessToken(userId, "test@example.com");
        UUID extracted = jwtService.extractUserId(token);
        assertEquals(userId, extracted);
    }

    // --- extractEmail ---

    @Test
    void extractEmail_validToken_returnsEmail() {
        String token = jwtService.generateAccessToken(userId, "test@example.com");
        String extracted = jwtService.extractEmail(token);
        assertEquals("test@example.com", extracted);
    }

    // --- isTokenValid ---

    @Test
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateAccessToken(userId, "test@example.com");
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void isTokenValid_invalidToken_returnsFalse() {
        assertFalse(jwtService.isTokenValid("invalid.token.here"));
    }

    @Test
    void isTokenValid_nullToken_returnsFalse() {
        assertFalse(jwtService.isTokenValid(null));
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        // Create a JwtService with 0ms expiration to produce instantly expired tokens
        JwtService expiredService = new JwtService(TEST_SECRET, 0L, 0L,
                refreshTokenRepository, appUserRepository);

        String token = expiredService.generateAccessToken(userId, "test@example.com");

        // Token may be immediately expired, but timing-sensitive.
        // At minimum, verify it doesn't throw unexpectedly
        assertFalse(expiredService.isTokenValid(token));
    }

    // --- validateRefreshToken ---

    @Test
    void validateRefreshToken_validToken_returnsRefreshToken() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String tokenValue = jwtService.generateRefreshToken(userId);

        RefreshToken storedToken = new RefreshToken();
        storedToken.setToken(tokenValue);
        storedToken.setAppUser(appUser);
        storedToken.setExpiresAt(ZonedDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));

        RefreshToken result = jwtService.validateRefreshToken(tokenValue);

        assertNotNull(result);
        assertEquals(tokenValue, result.getToken());
        assertEquals(appUser, result.getAppUser());
    }

    @Test
    void validateRefreshToken_invalidJwt_throwsJwtException() {
        assertThrows(JwtException.class, () -> jwtService.validateRefreshToken("invalid.token"));
    }

    @Test
    void validateRefreshToken_notInDatabase_throwsJwtException() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String tokenValue = jwtService.generateRefreshToken(userId);
        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.empty());

        assertThrows(JwtException.class, () -> jwtService.validateRefreshToken(tokenValue));
    }

    @Test
    void validateRefreshToken_expiredInDatabase_throwsJwtException() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String tokenValue = jwtService.generateRefreshToken(userId);

        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setToken(tokenValue);
        expiredToken.setAppUser(appUser);
        expiredToken.setExpiresAt(ZonedDateTime.now().minusDays(1));

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(expiredToken));

        assertThrows(JwtException.class, () -> jwtService.validateRefreshToken(tokenValue));
        verify(refreshTokenRepository).delete(expiredToken);
    }

    // --- revokeRefreshToken ---

    @Test
    void revokeRefreshToken_tokenExists_deletesIt() {
        jwtService.revokeRefreshToken("someToken");

        verify(refreshTokenRepository).deleteByTokenValue("someToken");
    }

    @Test
    void revokeRefreshToken_tokenNotExists_noOp() {
        jwtService.revokeRefreshToken("nonexistent");

        verify(refreshTokenRepository).deleteByTokenValue("nonexistent");
    }

    // --- revokeAllUserRefreshTokens ---

    @Test
    void revokeAllUserRefreshTokens_existingUser_deletesAllTokens() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));

        jwtService.revokeAllUserRefreshTokens(userId);

        verify(refreshTokenRepository).deleteByAppUser(appUser);
    }

    @Test
    void revokeAllUserRefreshTokens_nonExistentUser_throwsEntityNotFoundException() {
        when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> jwtService.revokeAllUserRefreshTokens(userId));
    }

    // --- cleanupExpiredTokens ---

    @Test
    void cleanupExpiredTokens_callsRepositoryDelete() {
        jwtService.cleanupExpiredTokens();
        verify(refreshTokenRepository).deleteByExpiresAtBefore(any(ZonedDateTime.class));
    }
}
