package com.expensetracker.service;

import com.expensetracker.dto.request.LoginRequest;
import com.expensetracker.dto.request.RegisterRequest;
import com.expensetracker.dto.response.AuthResponse;
import com.expensetracker.dto.response.UserDto;
import com.expensetracker.model.Organization;
import com.expensetracker.model.RefreshToken;
import com.expensetracker.model.User;
import com.expensetracker.model.enums.Role;
import com.expensetracker.repository.OrganizationRepository;
import com.expensetracker.repository.RefreshTokenRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       RefreshTokenService refreshTokenService,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validate organization exists and is active
        Organization organization = organizationRepository.findByIdAndIsActiveTrue(request.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Organization not found or inactive"));

        // Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        // Create user with EMPLOYEE role
        User user = User.builder()
                .organization(organization)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.EMPLOYEE)
                .isActive(true)
                .failedLoginAttempts(0)
                .build();

        User savedUser = userRepository.save(user);

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                savedUser.getId(), organization.getId(), savedUser.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(savedUser);

        return buildAuthResponse(accessToken, refreshToken, savedUser, organization);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is deactivated");
        }

        // Check account lockout
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Account is locked. Try again after " + user.getLockedUntil());
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Reset failed attempts on successful login
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Load organization for response
        Organization organization = user.getOrganization();

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getTenantId(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user);

        return buildAuthResponse(accessToken, refreshToken, user, organization);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = refreshTokenService.hashToken(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        // Reuse detection: if token is already revoked, revoke all tokens for user
        if (storedToken.getIsRevoked()) {
            refreshTokenService.revokeAllForUser(storedToken.getUserId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Refresh token reuse detected. All sessions revoked.");
        }

        // Check expiry
        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            storedToken.setIsRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        User user = storedToken.getUser();
        Organization organization = user.getOrganization();

        // Rotate token
        String newRawRefreshToken = refreshTokenService.rotateRefreshToken(storedToken, user);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getTenantId(), user.getRole().name());

        return buildAuthResponse(accessToken, newRawRefreshToken, user, organization);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = refreshTokenService.hashToken(rawRefreshToken);

        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setIsRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES));
        }

        userRepository.save(user);
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken,
                                           User user, Organization organization) {
        String managerName = null;
        if (user.getManager() != null) {
            managerName = user.getManager().getFirstName() + " " + user.getManager().getLastName();
        }

        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .organizationId(organization.getId())
                .organizationName(organization.getName())
                .managerId(user.getManagerId())
                .managerName(managerName)
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDto)
                .build();
    }
}
