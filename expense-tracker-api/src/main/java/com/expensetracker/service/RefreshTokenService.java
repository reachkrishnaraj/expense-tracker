package com.expensetracker.service;

import com.expensetracker.model.RefreshToken;
import com.expensetracker.model.User;
import com.expensetracker.repository.RefreshTokenRepository;
import com.expensetracker.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               JwtTokenProvider jwtTokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Creates a new refresh token for the given user.
     *
     * @param user the user entity (needed for JPA relationship)
     * @return the raw (unhashed) refresh token string
     */
    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = jwtTokenProvider.generateRefreshToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .isRevoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    /**
     * Rotates the refresh token: revokes old, creates new, links via replaced_by_id.
     *
     * @param oldToken the existing RefreshToken entity to rotate
     * @param user     the user entity
     * @return the raw (unhashed) new refresh token string
     */
    @Transactional
    public String rotateRefreshToken(RefreshToken oldToken, User user) {
        String rawToken = jwtTokenProvider.generateRefreshToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .isRevoked(false)
                .build();

        RefreshToken savedNewToken = refreshTokenRepository.save(newToken);

        oldToken.setIsRevoked(true);
        oldToken.setReplacedById(savedNewToken.getId());
        refreshTokenRepository.save(oldToken);

        return rawToken;
    }

    /**
     * Revokes all refresh tokens for a user (used in reuse detection).
     */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    /**
     * Hashes a raw refresh token using SHA-256.
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
