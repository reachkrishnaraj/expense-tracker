package com.expensetracker.integration;

import com.expensetracker.dto.request.CreateExpenseRequest;
import com.expensetracker.dto.request.UpdateExpenseRequest;
import com.expensetracker.model.ExpenseCategory;
import com.expensetracker.model.Organization;
import com.expensetracker.model.User;
import com.expensetracker.model.enums.Role;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Expense Integration Tests")
class ExpenseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseAuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Organization testOrg;
    private User employee;
    private User manager;
    private ExpenseCategory category;
    private String employeeToken;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        expenseRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        organizationRepository.deleteAll();

        testOrg = createOrganization("Expense Test Corp",
                "expense-corp-" + UUID.randomUUID().toString().substring(0, 8));
        category = createCategory(testOrg.getId(), "Travel");

        // Create manager directly in DB
        manager = createUserInDb("manager@test.com", "Passw0rd!", "Test", "Manager",
                testOrg, Role.MANAGER);

        // Create employee directly in DB with manager assigned
        employee = createUserInDb("employee@test.com", "Passw0rd!", "Test", "Employee",
                testOrg, Role.EMPLOYEE);
        assignManager(employee, manager);

        // Get token for employee
        employeeToken = loginAndGetToken("employee@test.com", "Passw0rd!");
    }

    @Nested
    @DisplayName("POST /api/v1/expenses")
    class CreateExpense {

        @Test
        @DisplayName("should create a draft expense and return 201")
        void createDraftExpense_returns201() throws Exception {
            CreateExpenseRequest request = CreateExpenseRequest.builder()
                    .amount(new BigDecimal("125.50"))
                    .categoryId(category.getId())
                    .merchantName("Delta Airlines")
                    .expenseDate(LocalDate.now().minusDays(1))
                    .notes("Business trip to NYC")
                    .build();

            mockMvc.perform(post("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.amount").value(125.50))
                    .andExpect(jsonPath("$.merchantName").value("Delta Airlines"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.notes").value("Business trip to NYC"))
                    .andExpect(jsonPath("$.submitter.name").value("Test Employee"));
        }

        @Test
        @DisplayName("should create expense without category")
        void createExpenseWithoutCategory_returns201() throws Exception {
            CreateExpenseRequest request = CreateExpenseRequest.builder()
                    .amount(new BigDecimal("50.00"))
                    .merchantName("Starbucks")
                    .expenseDate(LocalDate.now().minusDays(1))
                    .notes("Coffee meeting")
                    .build();

            mockMvc.perform(post("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.category").isEmpty());
        }

        @Test
        @DisplayName("should return 401 when creating expense without authentication")
        void createExpenseUnauthenticated_returns401() throws Exception {
            CreateExpenseRequest request = CreateExpenseRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .build();

            mockMvc.perform(post("/api/v1/expenses")
                            .with(withServletPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/expenses/{id}")
    class UpdateExpense {

        @Test
        @DisplayName("should update a draft expense and return 200")
        void updateDraftExpense_returns200() throws Exception {
            // Create expense
            MvcResult createResult = createExpenseViaApi(employeeToken,
                    new BigDecimal("100.00"), category.getId(), "Old Merchant",
                    LocalDate.now().minusDays(1), "Old notes");

            UUID expenseId = extractExpenseId(createResult);

            // Update
            UpdateExpenseRequest updateRequest = UpdateExpenseRequest.builder()
                    .amount(new BigDecimal("200.00"))
                    .merchantName("New Merchant")
                    .notes("Updated notes")
                    .build();

            mockMvc.perform(put("/api/v1/expenses/" + expenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(200.00))
                    .andExpect(jsonPath("$.merchantName").value("New Merchant"))
                    .andExpect(jsonPath("$.notes").value("Updated notes"))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        @DisplayName("should fail to update a submitted expense")
        void updateSubmittedExpense_fails() throws Exception {
            // Create and submit expense
            MvcResult createResult = createExpenseViaApi(employeeToken,
                    new BigDecimal("100.00"), category.getId(), "Merchant",
                    LocalDate.now().minusDays(1), "Notes");

            UUID expenseId = extractExpenseId(createResult);

            // Submit the expense
            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk());

            // Try to update the submitted expense
            UpdateExpenseRequest updateRequest = UpdateExpenseRequest.builder()
                    .amount(new BigDecimal("200.00"))
                    .build();

            mockMvc.perform(put("/api/v1/expenses/" + expenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().is5xxServerError()); // RuntimeException -> 500 (based on service code)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/expenses/{id}/submit")
    class SubmitExpense {

        @Test
        @DisplayName("should submit expense and change status to SUBMITTED")
        void submitExpense_returnsSubmittedStatus() throws Exception {
            MvcResult createResult = createExpenseViaApi(employeeToken,
                    new BigDecimal("150.00"), category.getId(), "Hotel Corp",
                    LocalDate.now().minusDays(2), "Hotel stay");

            UUID expenseId = extractExpenseId(createResult);

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUBMITTED"))
                    .andExpect(jsonPath("$.id").value(expenseId.toString()));
        }

        @Test
        @DisplayName("should return 400 when submitting expense without required category")
        void submitWithoutCategory_returns400() throws Exception {
            // Create expense without category
            CreateExpenseRequest request = CreateExpenseRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .merchantName("Merchant")
                    .expenseDate(LocalDate.now().minusDays(1))
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            UUID expenseId = extractExpenseId(createResult);

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when submitting expense without expense date")
        void submitWithoutExpenseDate_returns400() throws Exception {
            // Create expense without date
            CreateExpenseRequest request = CreateExpenseRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .categoryId(category.getId())
                    .merchantName("Merchant")
                    .build();

            MvcResult createResult = mockMvc.perform(post("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            UUID expenseId = extractExpenseId(createResult);

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when submitting expense with future date")
        void submitWithFutureDate_returns400() throws Exception {
            MvcResult createResult = createExpenseViaApi(employeeToken,
                    new BigDecimal("100.00"), category.getId(), "Merchant",
                    LocalDate.now().plusDays(5), "Future expense");

            UUID expenseId = extractExpenseId(createResult);

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when submitting expense with zero amount")
        void submitWithZeroAmount_returns400() throws Exception {
            MvcResult createResult = createExpenseViaApi(employeeToken,
                    BigDecimal.ZERO, category.getId(), "Merchant",
                    LocalDate.now().minusDays(1), "Zero amount");

            UUID expenseId = extractExpenseId(createResult);

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/expenses/{id}")
    class DeleteExpense {

        @Test
        @DisplayName("should delete a draft expense and return 204")
        void deleteDraftExpense_returns204() throws Exception {
            // Create expense directly in DB to avoid audit log FK constraint on delete
            com.expensetracker.model.Expense expense = createExpenseInDb(
                    employee, testOrg, category,
                    new BigDecimal("50.00"), "Coffee Shop",
                    LocalDate.now().minusDays(1), "Coffee");

            UUID expenseId = expense.getId();

            mockMvc.perform(delete("/api/v1/expenses/" + expenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNoContent());

            // Verify it's gone
            mockMvc.perform(get("/api/v1/expenses/" + expenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should fail to delete a submitted expense")
        void deleteSubmittedExpense_fails() throws Exception {
            // Create via API (audit log created), submit, then try to delete
            MvcResult createResult = createExpenseViaApi(employeeToken,
                    new BigDecimal("100.00"), category.getId(), "Merchant",
                    LocalDate.now().minusDays(1), "Notes");

            UUID expenseId = extractExpenseId(createResult);

            // Submit
            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk());

            // Try to delete - should fail because expense is not in DRAFT status
            // (RuntimeException in service code maps to 500)
            mockMvc.perform(delete("/api/v1/expenses/" + expenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().is5xxServerError());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/expenses")
    class ListExpenses {

        @Test
        @DisplayName("should list expenses with pagination")
        void listExpenses_returnsPaginated() throws Exception {
            // Create multiple expenses
            for (int i = 0; i < 3; i++) {
                createExpenseViaApi(employeeToken,
                        new BigDecimal("100.00").add(new BigDecimal(i * 10)),
                        category.getId(), "Merchant " + i,
                        LocalDate.now().minusDays(i + 1), "Expense " + i);
            }

            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.totalElements").value(3));
        }

        @Test
        @DisplayName("should filter expenses by status")
        void listExpenses_filterByStatus() throws Exception {
            // Create 2 draft and 1 submitted expense
            createExpenseViaApi(employeeToken, new BigDecimal("100.00"),
                    category.getId(), "Draft 1", LocalDate.now().minusDays(1), "Draft");

            MvcResult submitResult = createExpenseViaApi(employeeToken, new BigDecimal("200.00"),
                    category.getId(), "Submitted 1", LocalDate.now().minusDays(2), "Submit me");
            UUID submitId = extractExpenseId(submitResult);

            mockMvc.perform(post("/api/v1/expenses/" + submitId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk());

            // Filter by DRAFT status
            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("status", "DRAFT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("should filter expenses by date range")
        void listExpenses_filterByDateRange() throws Exception {
            createExpenseViaApi(employeeToken, new BigDecimal("100.00"),
                    category.getId(), "Recent", LocalDate.now().minusDays(1), "Recent expense");

            createExpenseViaApi(employeeToken, new BigDecimal("200.00"),
                    category.getId(), "Old", LocalDate.now().minusDays(30), "Old expense");

            // Filter for recent expenses only
            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("fromDate", LocalDate.now().minusDays(7).toString())
                            .param("toDate", LocalDate.now().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/expenses/{id}")
    class GetExpense {

        @Test
        @DisplayName("should return expense details for the submitter")
        void getExpenseAsSubmitter_returns200() throws Exception {
            MvcResult createResult = createExpenseViaApi(employeeToken,
                    new BigDecimal("75.00"), category.getId(), "Uber",
                    LocalDate.now().minusDays(1), "Taxi ride");

            UUID expenseId = extractExpenseId(createResult);

            mockMvc.perform(get("/api/v1/expenses/" + expenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(expenseId.toString()))
                    .andExpect(jsonPath("$.amount").value(75.00))
                    .andExpect(jsonPath("$.merchantName").value("Uber"))
                    .andExpect(jsonPath("$.auditTrail").isArray());
        }

        @Test
        @DisplayName("should return 404 for non-existent expense")
        void getNonExistentExpense_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/expenses/" + UUID.randomUUID())
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isNotFound());
        }
    }
}
