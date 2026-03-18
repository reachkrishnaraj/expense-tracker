package com.expensetracker.integration;

import com.expensetracker.dto.request.ApprovalRequest;
import com.expensetracker.dto.request.BulkApprovalRequest;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Approval Integration Tests")
class ApprovalIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseAuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private Organization testOrg;
    private User employee;
    private User manager;
    private User admin;
    private ExpenseCategory category;
    private String employeeToken;
    private String managerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        expenseRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.deleteAll();
        organizationRepository.deleteAll();

        testOrg = createOrganization("Approval Test Corp",
                "approval-corp-" + UUID.randomUUID().toString().substring(0, 8));
        category = createCategory(testOrg.getId(), "Travel");

        // Create admin
        admin = createUserInDb("admin@test.com", "Passw0rd!", "Test", "Admin",
                testOrg, Role.ADMIN);

        // Create manager
        manager = createUserInDb("manager@test.com", "Passw0rd!", "Test", "Manager",
                testOrg, Role.MANAGER);

        // Create employee with manager assigned
        employee = createUserInDb("employee@test.com", "Passw0rd!", "Test", "Employee",
                testOrg, Role.EMPLOYEE);
        assignManager(employee, manager);

        // Login users to get tokens
        employeeToken = loginAndGetToken("employee@test.com", "Passw0rd!");
        managerToken = loginAndGetToken("manager@test.com", "Passw0rd!");
        adminToken = loginAndGetToken("admin@test.com", "Passw0rd!");
    }

    /**
     * Helper: Create an expense and submit it, returning the expense ID.
     */
    private UUID createAndSubmitExpense(String token, BigDecimal amount, String merchant) throws Exception {
        MvcResult createResult = createExpenseViaApi(token, amount, category.getId(),
                merchant, LocalDate.now().minusDays(1), "Test expense");
        UUID expenseId = extractExpenseId(createResult);

        mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/submit")
                        .with(withServletPath())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        return expenseId;
    }

    @Nested
    @DisplayName("Full Approval Workflow: Create -> Submit -> Approve")
    class ApproveWorkflow {

        @Test
        @DisplayName("should approve expense through full workflow")
        void fullApproveWorkflow() throws Exception {
            // 1. Employee creates and submits expense
            UUID expenseId = createAndSubmitExpense(employeeToken, new BigDecimal("250.00"), "Delta Airlines");

            // 2. Manager approves
            ApprovalRequest approvalReq = new ApprovalRequest();
            approvalReq.setComment("Looks good, approved!");

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/approve")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(approvalReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.id").value(expenseId.toString()));

            // 3. Verify expense detail shows approved status
            mockMvc.perform(get("/api/v1/expenses/" + expenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        @Test
        @DisplayName("admin should be able to approve any expense in their tenant")
        void adminApprovesExpense() throws Exception {
            UUID expenseId = createAndSubmitExpense(employeeToken, new BigDecimal("300.00"), "Hotel Corp");

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/approve")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ApprovalRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }
    }

    @Nested
    @DisplayName("Full Rejection and Resubmission Workflow")
    class RejectAndResubmitWorkflow {

        @Test
        @DisplayName("should handle reject -> edit -> resubmit -> approve workflow")
        void fullRejectResubmitWorkflow() throws Exception {
            // 1. Create and submit
            UUID expenseId = createAndSubmitExpense(employeeToken, new BigDecimal("500.00"), "Luxury Hotel");

            // 2. Manager rejects with comment
            ApprovalRequest rejectReq = new ApprovalRequest();
            rejectReq.setComment("Amount too high, please provide justification");

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/reject")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(rejectReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));

            // 3. Employee edits the rejected expense (update amount and add notes)
            UpdateExpenseRequest updateReq = UpdateExpenseRequest.builder()
                    .amount(new BigDecimal("350.00"))
                    .notes("Reduced amount - company policy compliant")
                    .build();

            mockMvc.perform(put("/api/v1/expenses/" + expenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(350.00));

            // 4. Employee resubmits
            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/submit")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUBMITTED"));

            // 5. Manager approves the resubmitted expense
            ApprovalRequest approveReq = new ApprovalRequest();
            approveReq.setComment("Approved after revision");

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/approve")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(approveReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            // 6. Verify audit trail has full history
            mockMvc.perform(get("/api/v1/expenses/" + expenseId)
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"))
                    .andExpect(jsonPath("$.auditTrail").isArray())
                    .andExpect(jsonPath("$.auditTrail.length()").value(5)); // CREATED, SUBMITTED, REJECTED, RESUBMITTED, APPROVED
        }

        @Test
        @DisplayName("should require comment for rejection")
        void rejectWithoutComment_returns400() throws Exception {
            UUID expenseId = createAndSubmitExpense(employeeToken, new BigDecimal("100.00"), "Merchant");

            // Try to reject without comment
            ApprovalRequest rejectReq = new ApprovalRequest();
            // comment is null

            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/reject")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(rejectReq)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Manager Authorization")
    class ManagerAuthorization {

        @Test
        @DisplayName("manager can only approve expenses assigned to them")
        void managerCanOnlyApproveAssignedExpenses() throws Exception {
            // Create another manager
            User otherManager = createUserInDb("othermanager@test.com", "Passw0rd!",
                    "Other", "Manager", testOrg, Role.MANAGER);
            String otherManagerToken = loginAndGetToken("othermanager@test.com", "Passw0rd!");

            // Employee's expense is assigned to 'manager', not 'otherManager'
            UUID expenseId = createAndSubmitExpense(employeeToken, new BigDecimal("200.00"), "Merchant");

            // Other manager tries to approve
            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/approve")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + otherManagerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ApprovalRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("employee cannot approve expenses")
        void employeeCannotApprove() throws Exception {
            UUID expenseId = createAndSubmitExpense(employeeToken, new BigDecimal("100.00"), "Merchant");

            // Employee tries to approve their own expense
            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/approve")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ApprovalRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should not approve an already approved expense")
        void approveAlreadyApproved_returnsConflict() throws Exception {
            UUID expenseId = createAndSubmitExpense(employeeToken, new BigDecimal("100.00"), "Merchant");

            // First approve
            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/approve")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ApprovalRequest())))
                    .andExpect(status().isOk());

            // Try to approve again
            mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/approve")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ApprovalRequest())))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("Pending Approvals")
    class PendingApprovals {

        @Test
        @DisplayName("should list pending approvals for manager")
        void listPendingForManager() throws Exception {
            // Create and submit 2 expenses
            createAndSubmitExpense(employeeToken, new BigDecimal("100.00"), "Merchant 1");
            createAndSubmitExpense(employeeToken, new BigDecimal("200.00"), "Merchant 2");

            mockMvc.perform(get("/api/v1/approvals/pending")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("admin should see all pending expenses in their tenant")
        void adminSeesAllPending() throws Exception {
            createAndSubmitExpense(employeeToken, new BigDecimal("100.00"), "Merchant");

            mockMvc.perform(get("/api/v1/approvals/pending")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    @Nested
    @DisplayName("Bulk Approval")
    class BulkApproval {

        @Test
        @DisplayName("should bulk approve multiple expenses")
        void bulkApprove_succeeds() throws Exception {
            UUID id1 = createAndSubmitExpense(employeeToken, new BigDecimal("100.00"), "Merchant 1");
            UUID id2 = createAndSubmitExpense(employeeToken, new BigDecimal("200.00"), "Merchant 2");
            UUID id3 = createAndSubmitExpense(employeeToken, new BigDecimal("300.00"), "Merchant 3");

            BulkApprovalRequest bulkReq = new BulkApprovalRequest();
            bulkReq.setAction("APPROVE");
            bulkReq.setExpenseIds(List.of(id1, id2, id3));
            bulkReq.setComment("Batch approved");

            mockMvc.perform(post("/api/v1/approvals/bulk")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bulkReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.processed").value(3))
                    .andExpect(jsonPath("$.skipped").value(0))
                    .andExpect(jsonPath("$.results.length()").value(3));

            // Verify all are approved
            for (UUID id : List.of(id1, id2, id3)) {
                mockMvc.perform(get("/api/v1/expenses/" + id)
                                .with(withServletPath())
                                .header("Authorization", "Bearer " + employeeToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("APPROVED"));
            }
        }

        @Test
        @DisplayName("should skip non-existent expenses during bulk approve")
        void bulkApproveWithNonExistent_skips() throws Exception {
            UUID id1 = createAndSubmitExpense(employeeToken, new BigDecimal("100.00"), "Merchant 1");
            UUID nonExistentId = UUID.randomUUID();

            BulkApprovalRequest bulkReq = new BulkApprovalRequest();
            bulkReq.setAction("APPROVE");
            bulkReq.setExpenseIds(List.of(id1, nonExistentId));

            mockMvc.perform(post("/api/v1/approvals/bulk")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bulkReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.processed").value(1))
                    .andExpect(jsonPath("$.skipped").value(1));
        }

        @Test
        @DisplayName("should bulk reject with required comment")
        void bulkReject_succeeds() throws Exception {
            UUID id1 = createAndSubmitExpense(employeeToken, new BigDecimal("100.00"), "Merchant 1");
            UUID id2 = createAndSubmitExpense(employeeToken, new BigDecimal("200.00"), "Merchant 2");

            BulkApprovalRequest bulkReq = new BulkApprovalRequest();
            bulkReq.setAction("REJECT");
            bulkReq.setExpenseIds(List.of(id1, id2));
            bulkReq.setComment("Budget exceeded for this quarter");

            mockMvc.perform(post("/api/v1/approvals/bulk")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bulkReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.processed").value(2))
                    .andExpect(jsonPath("$.skipped").value(0));
        }

        @Test
        @DisplayName("should return 400 for bulk reject without comment")
        void bulkRejectWithoutComment_returns400() throws Exception {
            UUID id1 = createAndSubmitExpense(employeeToken, new BigDecimal("100.00"), "Merchant 1");

            BulkApprovalRequest bulkReq = new BulkApprovalRequest();
            bulkReq.setAction("REJECT");
            bulkReq.setExpenseIds(List.of(id1));
            // No comment

            mockMvc.perform(post("/api/v1/approvals/bulk")
                            .with(withServletPath())
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bulkReq)))
                    .andExpect(status().isBadRequest());
        }
    }
}
