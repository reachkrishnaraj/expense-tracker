package com.expensetracker.service;

import com.expensetracker.dto.request.BulkApprovalRequest;
import com.expensetracker.dto.response.BulkApprovalResultDto;
import com.expensetracker.dto.response.ExpenseDto;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseAuditLog;
import com.expensetracker.model.User;
import com.expensetracker.model.enums.AuditAction;
import com.expensetracker.model.enums.ExpenseStatus;
import com.expensetracker.repository.ExpenseAuditLogRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Returns pending (SUBMITTED) expenses for approval.
     * ADMIN sees all SUBMITTED expenses in the tenant.
     * MANAGER sees only SUBMITTED expenses assigned to them.
     */
    @Transactional(readOnly = true)
    public Page<ExpenseDto> getPendingApprovals(UUID tenantId, UUID currentUserId, String role,
                                                 UUID submitterFilter, UUID categoryFilter,
                                                 Pageable pageable) {
        // Enforce FIFO ordering: oldest first by createdAt
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        Page<Expense> expenses;

        if ("ADMIN".equals(role)) {
            expenses = expenseRepository.findPendingForAdmin(
                    tenantId, ExpenseStatus.SUBMITTED,
                    submitterFilter, categoryFilter, sortedPageable);
        } else {
            // MANAGER: only expenses assigned to this manager
            expenses = expenseRepository.findPendingForManager(
                    tenantId, currentUserId, ExpenseStatus.SUBMITTED,
                    submitterFilter, categoryFilter, sortedPageable);
        }

        return expenses.map(ExpenseDto::from);
    }

    /**
     * Approves an expense. Only the assigned manager or an ADMIN can approve.
     */
    @Transactional
    public ExpenseDto approveExpense(UUID expenseId, String comment) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Expense not found"));

        assertSubmittedStatus(expense);
        assertAuthorized(expense, currentUserId);

        String oldStatus = expense.getStatus().name();

        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setApprovedAt(LocalDateTime.now());

        // Set approvedBy via the relationship (since approvedById is insertable=false, updatable=false)
        User approver = userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Current user not found"));
        expense.setApprovedBy(approver);

        Expense saved = expenseRepository.save(expense);

        createAuditLog(saved, AuditAction.APPROVED, approver, oldStatus, ExpenseStatus.APPROVED.name(), comment);

        log.info("Expense {} approved by user {}", expenseId, currentUserId);

        return ExpenseDto.from(saved);
    }

    /**
     * Rejects an expense. Comment is required. Only the assigned manager or an ADMIN can reject.
     */
    @Transactional
    public ExpenseDto rejectExpense(UUID expenseId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Comment is required for rejection");
        }

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Expense not found"));

        assertSubmittedStatus(expense);
        assertAuthorized(expense, currentUserId);

        String oldStatus = expense.getStatus().name();

        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setRejectionComment(comment);

        User rejector = userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Current user not found"));

        Expense saved = expenseRepository.save(expense);

        createAuditLog(saved, AuditAction.REJECTED, rejector, oldStatus, ExpenseStatus.REJECTED.name(), comment);

        log.info("Expense {} rejected by user {}", expenseId, currentUserId);

        return ExpenseDto.from(saved);
    }

    /**
     * Bulk approve or reject expenses. Max 50 IDs. Comment required for REJECT.
     */
    @Transactional
    public BulkApprovalResultDto bulkAction(BulkApprovalRequest request) {
        boolean isReject = "REJECT".equals(request.getAction());

        if (isReject && (request.getComment() == null || request.getComment().isBlank())) {
            throw new ResponseStatusException(BAD_REQUEST, "Comment is required for bulk rejection");
        }

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        User actor = userRepository.findByIdAndTenantId(currentUserId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Current user not found"));

        List<BulkApprovalResultDto.BulkResultItem> results = new ArrayList<>();
        int processed = 0;
        int skipped = 0;

        for (UUID expenseId : request.getExpenseIds()) {
            try {
                // Find expense
                Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                        .orElse(null);

                if (expense == null) {
                    skipped++;
                    results.add(BulkApprovalResultDto.BulkResultItem.builder()
                            .expenseId(expenseId)
                            .status("SKIPPED")
                            .reason("Expense not found")
                            .build());
                    continue;
                }

                // Check status
                if (expense.getStatus() != ExpenseStatus.SUBMITTED) {
                    skipped++;
                    results.add(BulkApprovalResultDto.BulkResultItem.builder()
                            .expenseId(expenseId)
                            .status("SKIPPED")
                            .reason("Expense is not in SUBMITTED status (current: " + expense.getStatus() + ")")
                            .build());
                    continue;
                }

                // Check authorization
                if (!isAuthorized(expense, currentUserId)) {
                    skipped++;
                    results.add(BulkApprovalResultDto.BulkResultItem.builder()
                            .expenseId(expenseId)
                            .status("SKIPPED")
                            .reason("Not authorized to act on this expense")
                            .build());
                    continue;
                }

                String oldStatus = expense.getStatus().name();

                if (isReject) {
                    expense.setStatus(ExpenseStatus.REJECTED);
                    expense.setRejectionComment(request.getComment());
                    expenseRepository.save(expense);
                    createAuditLog(expense, AuditAction.REJECTED, actor, oldStatus,
                            ExpenseStatus.REJECTED.name(), request.getComment());
                } else {
                    expense.setStatus(ExpenseStatus.APPROVED);
                    expense.setApprovedAt(LocalDateTime.now());
                    expense.setApprovedBy(actor);
                    expenseRepository.save(expense);
                    createAuditLog(expense, AuditAction.APPROVED, actor, oldStatus,
                            ExpenseStatus.APPROVED.name(), request.getComment());
                }

                processed++;
                results.add(BulkApprovalResultDto.BulkResultItem.builder()
                        .expenseId(expenseId)
                        .status("SUCCESS")
                        .build());

            } catch (Exception e) {
                skipped++;
                results.add(BulkApprovalResultDto.BulkResultItem.builder()
                        .expenseId(expenseId)
                        .status("SKIPPED")
                        .reason("Error: " + e.getMessage())
                        .build());
            }
        }

        log.info("Bulk {} completed: {} processed, {} skipped", request.getAction(), processed, skipped);

        return BulkApprovalResultDto.builder()
                .processed(processed)
                .skipped(skipped)
                .results(results)
                .build();
    }

    // --- Private helpers ---

    private void assertSubmittedStatus(Expense expense) {
        if (expense.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new ResponseStatusException(CONFLICT,
                    "Expense is not in SUBMITTED status (current: " + expense.getStatus() + ")");
        }
    }

    private void assertAuthorized(Expense expense, UUID currentUserId) {
        if (!isAuthorized(expense, currentUserId)) {
            throw new ResponseStatusException(FORBIDDEN,
                    "You are not authorized to approve/reject this expense");
        }
    }

    private boolean isAuthorized(Expense expense, UUID currentUserId) {
        // ADMIN can act on any expense in their tenant
        if (SecurityUtils.isAdmin()) {
            return true;
        }
        // MANAGER can only act on expenses assigned to them
        return expense.getManagerId() != null && expense.getManagerId().equals(currentUserId);
    }

    private void createAuditLog(Expense expense, AuditAction action, User performedBy,
                                 String oldStatus, String newStatus, String comment) {
        ExpenseAuditLog auditLog = ExpenseAuditLog.builder()
                .expense(expense)
                .action(action)
                .performedBy(performedBy)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .comment(comment)
                .build();
        auditLogRepository.save(auditLog);
    }
}
