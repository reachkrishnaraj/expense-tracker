package com.expensetracker.controller;

import com.expensetracker.dto.request.ApprovalRequest;
import com.expensetracker.dto.request.BulkApprovalRequest;
import com.expensetracker.dto.response.BulkApprovalResultDto;
import com.expensetracker.dto.response.ExpenseDto;
import com.expensetracker.security.SecurityUtils;
import com.expensetracker.service.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * GET /api/v1/approvals/pending
     * Returns paginated list of pending (SUBMITTED) expenses for approval.
     * ADMIN sees all; MANAGER sees only their assigned expenses.
     */
    @GetMapping("/approvals/pending")
    public ResponseEntity<Page<ExpenseDto>> getPendingApprovals(
            @RequestParam(required = false) UUID submitterId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        String role = SecurityUtils.getCurrentRole();

        int effectiveSize = Math.min(Math.max(1, size), 100);
        Page<ExpenseDto> pendingExpenses = approvalService.getPendingApprovals(
                tenantId, currentUserId, role, submitterId, categoryId,
                PageRequest.of(page, effectiveSize));

        return ResponseEntity.ok(pendingExpenses);
    }

    /**
     * POST /api/v1/expenses/{id}/approve
     * Approves a single expense.
     */
    @PostMapping("/expenses/{id}/approve")
    public ResponseEntity<ExpenseDto> approveExpense(
            @PathVariable UUID id,
            @RequestBody(required = false) ApprovalRequest request) {

        String comment = (request != null) ? request.getComment() : null;
        ExpenseDto result = approvalService.approveExpense(id, comment);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/expenses/{id}/reject
     * Rejects a single expense. Comment is required.
     */
    @PostMapping("/expenses/{id}/reject")
    public ResponseEntity<ExpenseDto> rejectExpense(
            @PathVariable UUID id,
            @RequestBody ApprovalRequest request) {

        String comment = (request != null) ? request.getComment() : null;
        ExpenseDto result = approvalService.rejectExpense(id, comment);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/approvals/bulk
     * Bulk approve or reject up to 50 expenses.
     */
    @PostMapping("/approvals/bulk")
    public ResponseEntity<BulkApprovalResultDto> bulkAction(
            @Valid @RequestBody BulkApprovalRequest request) {

        BulkApprovalResultDto result = approvalService.bulkAction(request);
        return ResponseEntity.ok(result);
    }
}
