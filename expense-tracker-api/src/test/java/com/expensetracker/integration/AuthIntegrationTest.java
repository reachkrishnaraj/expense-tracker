package com.expensetracker.integration;

import com.expensetracker.dto.request.LoginRequest;
import com.expensetracker.dto.request.RefreshRequest;
import com.expensetracker.dto.request.RegisterRequest;
import com.expensetracker.dto.response.AuthResponse;
import com.expensetracker.model.Organization;
import com.expensetracker.repository.ExpenseAuditLogRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth Integration Tests")
class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ExpenseAuditLogRepository auditLogRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Organization testOrg;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        expenseRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        organizationRepository.deleteAll();

        testOrg = createOrganization("Test Corp", "test-corp-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        @Test
        @DisplayName("should register user with valid data and return 201")
        void registerWithValidData_returns201() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("john@test.com")
                    .password("Passw0rd!")
                    .firstName("John")
                    .lastName("Doe")
                    .organizationId(testOrg.getId())
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value("john@test.com"))
                    .andExpect(jsonPath("$.user.firstName").value("John"))
                    .andExpect(jsonPath("$.user.lastName").value("Doe"))
                    .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
                    .andExpect(jsonPath("$.user.organizationId").value(testOrg.getId().toString()))
                    .andExpect(jsonPath("$.user.isActive").value(true));
        }

        @Test
        @DisplayName("should return 409 for duplicate email registration")
        void registerWithDuplicateEmail_returns409() throws Exception {
            // First registration
            registerUser("duplicate@test.com", "Passw0rd!", "First", "User", testOrg.getId());

            // Second registration with same email
            RegisterRequest request = RegisterRequest.builder()
                    .email("duplicate@test.com")
                    .password("Passw0rd!")
                    .firstName("Second")
                    .lastName("User")
                    .organizationId(testOrg.getId())
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should return 400 for password shorter than 8 characters")
        void registerWithShortPassword_returns400() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("short@test.com")
                    .password("short")
                    .firstName("Short")
                    .lastName("Pass")
                    .organizationId(testOrg.getId())
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for missing required fields")
        void registerWithMissingFields_returns400() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("")
                    .password("Passw0rd!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for invalid email format")
        void registerWithInvalidEmail_returns400() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("not-an-email")
                    .password("Passw0rd!")
                    .firstName("Bad")
                    .lastName("Email")
                    .organizationId(testOrg.getId())
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for non-existent organization ID")
        void registerWithNonExistentOrg_returns400() throws Exception {
            RegisterRequest request = RegisterRequest.builder()
                    .email("orgfail@test.com")
                    .password("Passw0rd!")
                    .firstName("No")
                    .lastName("Org")
                    .organizationId(UUID.randomUUID())
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("should login with valid credentials and return 200")
        void loginWithValidCredentials_returns200() throws Exception {
            // Register first
            registerUser("loginuser@test.com", "Passw0rd!", "Login", "User", testOrg.getId());

            // Login
            LoginRequest request = LoginRequest.builder()
                    .email("loginuser@test.com")
                    .password("Passw0rd!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value("loginuser@test.com"))
                    .andExpect(jsonPath("$.user.firstName").value("Login"))
                    .andExpect(jsonPath("$.user.lastName").value("User"));
        }

        @Test
        @DisplayName("should return 401 for invalid password")
        void loginWithInvalidPassword_returns401() throws Exception {
            registerUser("wrongpass@test.com", "Passw0rd!", "Wrong", "Pass", testOrg.getId());

            LoginRequest request = LoginRequest.builder()
                    .email("wrongpass@test.com")
                    .password("WrongPassword!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 401 for non-existent email")
        void loginWithNonExistentEmail_returns401() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("nonexistent@test.com")
                    .password("Passw0rd!")
                    .build();

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("should rotate tokens and return new access and refresh tokens")
        void refreshToken_returnsNewTokens() throws Exception {
            // Register and get tokens
            AuthResponse authResponse = registerUser("refresh@test.com", "Passw0rd!",
                    "Refresh", "User", testOrg.getId());

            String oldRefreshToken = authResponse.getRefreshToken();

            // Refresh
            RefreshRequest refreshReq = RefreshRequest.builder()
                    .refreshToken(oldRefreshToken)
                    .build();

            MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andReturn();

            AuthResponse newAuthResponse = objectMapper.readValue(
                    result.getResponse().getContentAsString(), AuthResponse.class);

            // Refresh token must be different (rotated)
            assertThat(newAuthResponse.getRefreshToken()).isNotEqualTo(oldRefreshToken);
            // Access token is present and valid
            assertThat(newAuthResponse.getAccessToken()).isNotBlank();
        }

        @Test
        @DisplayName("should return 401 when using already-rotated refresh token (reuse detection)")
        void refreshWithOldToken_returns401() throws Exception {
            AuthResponse authResponse = registerUser("reuse@test.com", "Passw0rd!",
                    "Reuse", "User", testOrg.getId());

            String oldRefreshToken = authResponse.getRefreshToken();

            // First refresh - should succeed
            RefreshRequest refreshReq = RefreshRequest.builder()
                    .refreshToken(oldRefreshToken)
                    .build();

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshReq)))
                    .andExpect(status().isOk());

            // Second refresh with old token - should fail (reuse detection)
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshReq)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Protected Endpoint Access")
    class ProtectedEndpoints {

        @Test
        @DisplayName("should return 401 when accessing protected endpoint without token")
        void accessProtectedWithoutToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 401 when accessing protected endpoint with invalid token")
        void accessProtectedWithInvalidToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer invalid-token-value")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should succeed when accessing protected endpoint with valid token")
        void accessProtectedWithValidToken_succeeds() throws Exception {
            AuthResponse authResponse = registerUser("protected@test.com", "Passw0rd!",
                    "Protected", "User", testOrg.getId());

            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + authResponse.getAccessToken())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 401 when Authorization header is missing Bearer prefix")
        void accessProtectedWithoutBearerPrefix_returns401() throws Exception {
            AuthResponse authResponse = registerUser("nobearer@test.com", "Passw0rd!",
                    "No", "Bearer", testOrg.getId());

            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", authResponse.getAccessToken())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }
}
