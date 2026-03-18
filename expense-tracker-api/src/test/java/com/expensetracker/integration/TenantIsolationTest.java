package com.expensetracker.integration;

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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Tenant Isolation Integration Tests")
class TenantIsolationTest extends BaseIntegrationTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseAuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    // Organization A
    private Organization orgA;
    private User employeeA;
    private User managerA;
    private ExpenseCategory categoryA;
    private String employeeAToken;

    // Organization B
    private Organization orgB;
    private User employeeB;
    private User managerB;
    private ExpenseCategory categoryB;
    private String employeeBToken;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        expenseRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        organizationRepository.deleteAll();

        // --- Set up Organization A ---
        orgA = createOrganization("Org Alpha",
                "org-alpha-" + UUID.randomUUID().toString().substring(0, 8));
        categoryA = createCategory(orgA.getId(), "Travel-A");

        managerA = createUserInDb("managera@orgA.com", "Passw0rd!", "Manager", "Alpha",
                orgA, Role.MANAGER);

        employeeA = createUserInDb("employeea@orgA.com", "Passw0rd!", "Employee", "Alpha",
                orgA, Role.EMPLOYEE);
        assignManager(employeeA, managerA);

        employeeAToken = loginAndGetToken("employeea@orgA.com", "Passw0rd!");

        // --- Set up Organization B ---
        orgB = createOrganization("Org Beta",
                "org-beta-" + UUID.randomUUID().toString().substring(0, 8));
        categoryB = createCategory(orgB.getId(), "Travel-B");

        managerB = createUserInDb("managerb@orgB.com", "Passw0rd!", "Manager", "Beta",
                orgB, Role.MANAGER);

        employeeB = createUserInDb("employeeb@orgB.com", "Passw0rd!", "Employee", "Beta",
                orgB, Role.EMPLOYEE);
        assignManager(employeeB, managerB);

        employeeBToken = loginAndGetToken("employeeb@orgB.com", "Passw0rd!");
    }

    @Nested
    @DisplayName("Cross-Tenant Expense Visibility")
    class ExpenseVisibility {

        @Test
        @DisplayName("user from Org A cannot see Org B expenses in list")
        void userFromOrgA_cannotSeeOrgBExpenses() throws Exception {
            // Employee A creates expenses in Org A
            createExpenseViaApi(employeeAToken, new BigDecimal("100.00"), categoryA.getId(),
                    "OrgA Merchant", LocalDate.now().minusDays(1), "Org A expense 1");
            createExpenseViaApi(employeeAToken, new BigDecimal("200.00"), categoryA.getId(),
                    "OrgA Merchant 2", LocalDate.now().minusDays(2), "Org A expense 2");

            // Employee B creates expenses in Org B
            createExpenseViaApi(employeeBToken, new BigDecimal("300.00"), categoryB.getId(),
                    "OrgB Merchant", LocalDate.now().minusDays(1), "Org B expense 1");

            // Employee A lists expenses -- should only see their own org's expenses
            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeAToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(2));

            // Employee B lists expenses -- should only see their own org's expenses
            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeBToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("user from Org A cannot access Org B expense by ID - returns 404")
        void userFromOrgA_cannotAccessOrgBExpenseById() throws Exception {
            // Employee B creates an expense
            MvcResult bResult = createExpenseViaApi(employeeBToken, new BigDecimal("300.00"),
                    categoryB.getId(), "OrgB Merchant", LocalDate.now().minusDays(1), "Org B expense");
            UUID orgBExpenseId = extractExpenseId(bResult);

            // Employee A tries to access Org B's expense by ID
            mockMvc.perform(get("/api/v1/expenses/" + orgBExpenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeAToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("user from Org B cannot access Org A expense by ID - returns 404")
        void userFromOrgB_cannotAccessOrgAExpenseById() throws Exception {
            // Employee A creates an expense
            MvcResult aResult = createExpenseViaApi(employeeAToken, new BigDecimal("100.00"),
                    categoryA.getId(), "OrgA Merchant", LocalDate.now().minusDays(1), "Org A expense");
            UUID orgAExpenseId = extractExpenseId(aResult);

            // Employee B tries to access Org A's expense by ID
            mockMvc.perform(get("/api/v1/expenses/" + orgAExpenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeBToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Cross-Tenant Approval Isolation")
    class ApprovalIsolation {

        @Test
        @DisplayName("manager from Org A cannot see Org B pending approvals")
        void managerFromOrgA_cannotSeeOrgBPendingApprovals() throws Exception {
            String managerAToken = loginAndGetToken("managera@orgA.com", "Passw0rd!");
            String managerBToken = loginAndGetToken("managerb@orgB.com", "Passw0rd!");

            // Employee A creates and submits expense (assigned to manager A)
            MvcResult aResult = createExpenseViaApi(employeeAToken, new BigDecimal("100.00"),
                    categoryA.getId(), "OrgA Merchant", LocalDate.now().minusDays(1), "Org A expense");
            UUID orgAExpenseId = extractExpenseId(aResult);
            mockMvc.perform(post("/api/v1/expenses/" + orgAExpenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeAToken))
                    .andExpect(status().isOk());

            // Employee B creates and submits expense (assigned to manager B)
            MvcResult bResult = createExpenseViaApi(employeeBToken, new BigDecimal("200.00"),
                    categoryB.getId(), "OrgB Merchant", LocalDate.now().minusDays(1), "Org B expense");
            UUID orgBExpenseId = extractExpenseId(bResult);
            mockMvc.perform(post("/api/v1/expenses/" + orgBExpenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeBToken))
                    .andExpect(status().isOk());

            // Manager A's pending list should only contain Org A expenses
            mockMvc.perform(get("/api/v1/approvals/pending")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerAToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(orgAExpenseId.toString()));

            // Manager B's pending list should only contain Org B expenses
            mockMvc.perform(get("/api/v1/approvals/pending")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerBToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(orgBExpenseId.toString()));
        }
    }

    @Nested
    @DisplayName("Cross-Tenant Admin Isolation")
    class AdminIsolation {

        @Test
        @DisplayName("admin from Org A only sees Org A data")
        void adminOrgA_onlySeesOrgAData() throws Exception {
            // Create admin for Org A
            User adminA = createUserInDb("admina@orgA.com", "Passw0rd!", "Admin", "Alpha",
                    orgA, Role.ADMIN);
            String adminAToken = loginAndGetToken("admina@orgA.com", "Passw0rd!");

            // Employee A creates expenses
            createExpenseViaApi(employeeAToken, new BigDecimal("100.00"), categoryA.getId(),
                    "OrgA Merchant", LocalDate.now().minusDays(1), "Org A expense");

            // Employee B creates expenses
            createExpenseViaApi(employeeBToken, new BigDecimal("200.00"), categoryB.getId(),
                    "OrgB Merchant", LocalDate.now().minusDays(1), "Org B expense");

            // Admin A lists all expenses - should only see Org A expenses
            mockMvc.perform(get("/api/v1/expenses")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + adminAToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("admin from Org B only sees Org B pending approvals")
        void adminOrgB_onlySeesOrgBPendingApprovals() throws Exception {
            User adminB = createUserInDb("adminb@orgB.com", "Passw0rd!", "Admin", "Beta",
                    orgB, Role.ADMIN);
            String adminBToken = loginAndGetToken("adminb@orgB.com", "Passw0rd!");

            // Employee A creates and submits expense
            MvcResult aResult = createExpenseViaApi(employeeAToken, new BigDecimal("100.00"),
                    categoryA.getId(), "OrgA Merchant", LocalDate.now().minusDays(1), "Org A expense");
            UUID orgAExpenseId = extractExpenseId(aResult);
            mockMvc.perform(post("/api/v1/expenses/" + orgAExpenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeAToken))
                    .andExpect(status().isOk());

            // Employee B creates and submits expense
            MvcResult bResult = createExpenseViaApi(employeeBToken, new BigDecimal("200.00"),
                    categoryB.getId(), "OrgB Merchant", LocalDate.now().minusDays(1), "Org B expense");
            UUID orgBExpenseId = extractExpenseId(bResult);
            mockMvc.perform(post("/api/v1/expenses/" + orgBExpenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeBToken))
                    .andExpect(status().isOk());

            // Admin B should only see Org B pending approvals
            mockMvc.perform(get("/api/v1/approvals/pending")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + adminBToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(orgBExpenseId.toString()));
        }
    }
}
