package com.expensetracker.service;

import com.expensetracker.dto.response.AuditLogDto;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final ExpenseAuditLogRepository auditLogRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Transactional
    public ExpenseAuditLog log(UUID expenseId, AuditAction action, UUID performedById,
                                String comment, ExpenseStatus oldStatus, ExpenseStatus newStatus) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new RuntimeException("Expense not found: " + expenseId));
        User performedBy = userRepository.findById(performedById)
                .orElseThrow(() -> new RuntimeException("User not found: " + performedById));

        ExpenseAuditLog auditLog = ExpenseAuditLog.builder()
                .expense(expense)
                .action(action)
                .performedBy(performedBy)
                .comment(comment)
                .oldStatus(oldStatus != null ? oldStatus.name() : null)
                .newStatus(newStatus != null ? newStatus.name() : null)
                .build();

        return auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> getAuditTrail(UUID expenseId) {
        return auditLogRepository.findByExpenseIdOrderByCreatedAtAsc(expenseId)
                .stream()
                .map(AuditLogDto::from)
                .collect(Collectors.toList());
    }
}
