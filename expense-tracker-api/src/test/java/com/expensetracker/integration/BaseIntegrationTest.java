package com.expensetracker.integration;

import com.expensetracker.dto.request.CreateExpenseRequest;
import com.expensetracker.dto.request.LoginRequest;
import com.expensetracker.dto.request.RegisterRequest;
import com.expensetracker.dto.response.AuthResponse;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseCategory;
import com.expensetracker.model.Organization;
import com.expensetracker.model.User;
import com.expensetracker.model.enums.ExpenseStatus;
import com.expensetracker.model.enums.Role;
import com.expensetracker.ratelimit.InMemoryRateLimiter;
import com.expensetracker.repository.ExpenseCategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.OrganizationRepository;
import com.expensetracker.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests.
 * Provides helper methods for common operations like user registration,
 * login, organization creation, and expense creation.
 *
 * Important: MockMvc's MockHttpServletRequest does not set servletPath by default,
 * but the application's filters use getServletPath() for path matching. We use a
 * RequestPostProcessor to ensure servletPath is properly set on every request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected OrganizationRepository organizationRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ExpenseCategoryRepository categoryRepository;

    @Autowired
    protected ExpenseRepository expenseRepositoryBase;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    /**
     * Clears the rate limiter state before each test to prevent rate limit
     * exhaustion across tests sharing the same Spring context.
     */
    @BeforeEach
    void resetRateLimiter() {
        try {
            Field bucketsField = InMemoryRateLimiter.class.getDeclaredField("buckets");
            bucketsField.setAccessible(true);
            ConcurrentHashMap<?, ?> buckets = (ConcurrentHashMap<?, ?>) bucketsField.get(rateLimiter);
            buckets.clear();
        } catch (Exception e) {
            // Silently continue if reflection fails
        }
    }

    /**
     * RequestPostProcessor that copies the requestURI into servletPath.
     * This is needed because MockMvc's MockHttpServletRequest leaves servletPath
     * empty by default, but the application's security filters use
     * getServletPath() for path-based decisions (e.g., skipping auth endpoints).
     */
    protected static RequestPostProcessor withServletPath() {
        return request -> {
            request.setServletPath(request.getRequestURI());
            return request;
        };
    }

    // ---- Helper: Create an Organization directly in DB ----

    protected Organization createOrganization(String name, String slug) {
        Organization org = Organization.builder()
                .name(name)
                .slug(slug)
                .currency("USD")
                .isActive(true)
                .build();
        return organizationRepository.save(org);
    }

    // ---- Helper: Create a category directly in DB ----

    protected ExpenseCategory createCategory(UUID tenantId, String name) {
        ExpenseCategory category = ExpenseCategory.builder()
                .tenantId(tenantId)
                .name(name)
                .isActive(true)
                .build();
        return categoryRepository.save(category);
    }

    // ---- Helper: Register a user via the API ----

    protected AuthResponse registerUser(String email, String password, String firstName,
                                         String lastName, UUID organizationId) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .password(password)
                .firstName(firstName)
                .lastName(lastName)
                .organizationId(organizationId)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .with(withServletPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    // ---- Helper: Login a user via the API ----

    protected AuthResponse loginUser(String email, String password) throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(email)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(withServletPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    // ---- Helper: Get access token by registering or logging in ----

    protected String getAuthToken(String email, String password, String firstName,
                                   String lastName, UUID organizationId) throws Exception {
        AuthResponse response = registerUser(email, password, firstName, lastName, organizationId);
        return response.getAccessToken();
    }

    // ---- Helper: Create a user directly in DB with a specific role ----

    protected User createUserInDb(String email, String password, String firstName,
                                   String lastName, Organization org, Role role) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .organization(org)
                .role(role)
                .isActive(true)
                .failedLoginAttempts(0)
                .build();
        return userRepository.save(user);
    }

    // ---- Helper: Assign a manager to a user directly in DB ----

    protected void assignManager(User employee, User manager) {
        employee.setManager(manager);
        userRepository.save(employee);
    }

    // ---- Helper: Create an expense via the API ----

    protected MvcResult createExpenseViaApi(String token, BigDecimal amount, UUID categoryId,
                                             String merchantName, LocalDate expenseDate,
                                             String notes) throws Exception {
        CreateExpenseRequest request = CreateExpenseRequest.builder()
                .amount(amount)
                .categoryId(categoryId)
                .merchantName(merchantName)
                .expenseDate(expenseDate)
                .notes(notes)
                .build();

        return mockMvc.perform(post("/api/v1/expenses")
                        .with(withServletPath())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    // ---- Helper: Create an expense directly in DB (no audit log) ----

    protected Expense createExpenseInDb(User submitter, Organization org, ExpenseCategory category,
                                         BigDecimal amount, String merchantName, LocalDate expenseDate,
                                         String notes) {
        Expense expense = Expense.builder()
                .tenantId(org.getId())
                .submitter(submitter)
                .status(ExpenseStatus.DRAFT)
                .currency(org.getCurrency())
                .amount(amount)
                .merchantName(merchantName)
                .expenseDate(expenseDate)
                .notes(notes)
                .build();
        if (category != null) {
            expense.setCategory(category);
        }
        return expenseRepositoryBase.save(expense);
    }

    // ---- Helper: Extract expense ID from JSON response ----

    protected UUID extractExpenseId(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        return UUID.fromString(jsonNode.get("id").asText());
    }

    // ---- Helper: Login user created in DB and return token ----

    protected String loginAndGetToken(String email, String password) throws Exception {
        AuthResponse response = loginUser(email, password);
        return response.getAccessToken();
    }

    // ---- Helper: Login user and return full auth response ----

    protected AuthResponse loginAndGetAuthResponse(String email, String password) throws Exception {
        return loginUser(email, password);
    }
}
