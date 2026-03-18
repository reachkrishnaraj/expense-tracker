package com.expensetracker.service;

import com.expensetracker.dto.request.CreateExpenseRequest;
import com.expensetracker.dto.request.UpdateExpenseRequest;
import com.expensetracker.dto.response.AuditLogDto;
import com.expensetracker.dto.response.ExpenseDetailDto;
import com.expensetracker.dto.response.ExpenseDto;
import com.expensetracker.dto.response.ExpenseSummaryDto;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseCategory;
import com.expensetracker.model.User;
import com.expensetracker.model.enums.AuditAction;
import com.expensetracker.model.enums.ExpenseStatus;
import com.expensetracker.repository.ExpenseCategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    @Transactional
    public ExpenseDto createExpense(CreateExpenseRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();

        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String currency = user.getOrganization() != null ? user.getOrganization().getCurrency() : "USD";

        Expense expense = Expense.builder()
                .tenantId(tenantId)
                .submitter(user)
                .status(ExpenseStatus.DRAFT)
                .currency(currency)
                .amount(req.getAmount())
                .merchantName(req.getMerchantName())
                .expenseDate(req.getExpenseDate())
                .notes(req.getNotes())
                .build();

        if (req.getCategoryId() != null) {
            ExpenseCategory category = categoryRepository.findByIdAndTenantId(req.getCategoryId(), tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Category not found: " + req.getCategoryId()));
            expense.setCategory(category);
        }

        expense = expenseRepository.save(expense);

        auditLogService.log(expense.getId(), AuditAction.CREATED, userId,
                "Expense created", null, ExpenseStatus.DRAFT);

        return ExpenseDto.from(expense);
    }

    @Transactional
    public ExpenseDto updateExpense(UUID expenseId, UpdateExpenseRequest req) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();

        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        // Assert current user is submitter
        if (!expense.getSubmitterId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the submitter can update this expense");
        }

        // Assert status is DRAFT or REJECTED
        if (expense.getStatus() != ExpenseStatus.DRAFT && expense.getStatus() != ExpenseStatus.REJECTED) {
            throw new RuntimeException("Expense can only be updated in DRAFT or REJECTED status");
        }

        // Update only non-null fields
        if (req.getAmount() != null) {
            expense.setAmount(req.getAmount());
        }
        if (req.getCategoryId() != null) {
            ExpenseCategory category = categoryRepository.findByIdAndTenantId(req.getCategoryId(), tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Category not found: " + req.getCategoryId()));
            expense.setCategory(category);
        }
        if (req.getMerchantName() != null) {
            expense.setMerchantName(req.getMerchantName());
        }
        if (req.getExpenseDate() != null) {
            expense.setExpenseDate(req.getExpenseDate());
        }
        if (req.getNotes() != null) {
            expense.setNotes(req.getNotes());
        }

        expense = expenseRepository.save(expense);
        return ExpenseDto.from(expense);
    }

    @Transactional(readOnly = true)
    public ExpenseDetailDto getExpense(UUID expenseId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();

        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));

        // Access check: submitter sees own, manager sees assigned team's, admin sees all
        boolean isSubmitter = expense.getSubmitterId().equals(userId);
        boolean isAssignedManager = expense.getManagerId() != null && expense.getManagerId().equals(userId);
        boolean isAdmin = SecurityUtils.isAdmin();

        if (!isSubmitter && !isAssignedManager && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have access to this expense");
        }

        List<AuditLogDto> auditTrail = auditLogService.getAuditTrail(expenseId);

        // Receipts: return empty list for now (M8 will handle file storage)
        List<com.expensetracker.dto.response.ReceiptDto> receipts = Collections.emptyList();

        return ExpenseDetailDto.from(expense, receipts, auditTrail);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseSummaryDto> listExpenses(UUID submitterId, ExpenseStatus status,
                                                 UUID categoryId, LocalDate fromDate,
                                                 LocalDate toDate, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Expense> cq = cb.createQuery(Expense.class);
        Root<Expense> root = cq.from(Expense.class);

        List<Predicate> predicates = new ArrayList<>();

        // Always filter by tenant
        predicates.add(cb.equal(root.get("tenantId"), tenantId));

        // Filter by submitter if provided
        if (submitterId != null) {
            predicates.add(cb.equal(root.get("submitterId"), submitterId));
        }

        // Filter by status if provided
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }

        // Filter by category if provided
        if (categoryId != null) {
            predicates.add(cb.equal(root.get("categoryId"), categoryId));
        }

        // Filter by date range
        if (fromDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), fromDate));
        }
        if (toDate != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), toDate));
        }

        // If not admin, restrict visibility
        if (!SecurityUtils.isAdmin()) {
            if (SecurityUtils.isManager()) {
                // Manager can see own expenses and team's expenses
                Predicate ownExpenses = cb.equal(root.get("submitterId"), userId);
                Predicate teamExpenses = cb.equal(root.get("managerId"), userId);
                predicates.add(cb.or(ownExpenses, teamExpenses));
            } else {
                // Employee can only see own expenses
                predicates.add(cb.equal(root.get("submitterId"), userId));
            }
        }

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get("createdAt")));

        // Count query for pagination
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Expense> countRoot = countQuery.from(Expense.class);
        List<Predicate> countPredicates = new ArrayList<>();

        countPredicates.add(cb.equal(countRoot.get("tenantId"), tenantId));
        if (submitterId != null) {
            countPredicates.add(cb.equal(countRoot.get("submitterId"), submitterId));
        }
        if (status != null) {
            countPredicates.add(cb.equal(countRoot.get("status"), status));
        }
        if (categoryId != null) {
            countPredicates.add(cb.equal(countRoot.get("categoryId"), categoryId));
        }
        if (fromDate != null) {
            countPredicates.add(cb.greaterThanOrEqualTo(countRoot.get("expenseDate"), fromDate));
        }
        if (toDate != null) {
            countPredicates.add(cb.lessThanOrEqualTo(countRoot.get("expenseDate"), toDate));
        }
        if (!SecurityUtils.isAdmin()) {
            if (SecurityUtils.isManager()) {
                Predicate ownExpenses = cb.equal(countRoot.get("submitterId"), userId);
                Predicate teamExpenses = cb.equal(countRoot.get("managerId"), userId);
                countPredicates.add(cb.or(ownExpenses, teamExpenses));
            } else {
                countPredicates.add(cb.equal(countRoot.get("submitterId"), userId));
            }
        }
        countQuery.select(cb.count(countRoot));
        countQuery.where(countPredicates.toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        // Data query with pagination
        TypedQuery<Expense> typedQuery = entityManager.createQuery(cq);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Expense> expenses = typedQuery.getResultList();

        List<ExpenseSummaryDto> content = expenses.stream()
                .map(ExpenseSummaryDto::from)
                .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    @Transactional
    public ExpenseDto submitExpense(UUID expenseId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();

        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));

        // Assert current user is submitter
        if (!expense.getSubmitterId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the submitter can submit this expense");
        }

        // Assert status is DRAFT or REJECTED
        if (expense.getStatus() != ExpenseStatus.DRAFT && expense.getStatus() != ExpenseStatus.REJECTED) {
            throw new RuntimeException("Expense can only be submitted from DRAFT or REJECTED status");
        }

        // Validate required fields
        if (expense.getAmount() == null || expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Amount must be greater than 0");
        }
        if (expense.getCategoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Category is required for submission");
        }
        // Validate category is active
        ExpenseCategory category = categoryRepository.findByIdAndTenantId(expense.getCategoryId(), tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Category not found"));
        if (!category.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Category is not active");
        }
        if (expense.getExpenseDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expense date is required for submission");
        }
        if (expense.getExpenseDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expense date cannot be in the future");
        }

        // Check submitter has a manager assigned
        User submitter = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (submitter.getManagerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No manager assigned");
        }

        // Determine audit action based on previous status
        ExpenseStatus oldStatus = expense.getStatus();
        AuditAction auditAction;
        if (oldStatus == ExpenseStatus.REJECTED) {
            expense.setRejectionComment(null);
            auditAction = AuditAction.RESUBMITTED;
        } else {
            auditAction = AuditAction.SUBMITTED;
        }

        // Snapshot manager_id onto expense record
        User managerUser = userRepository.findById(submitter.getManagerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Assigned manager not found"));
        expense.setManager(managerUser);

        // Set status to SUBMITTED
        expense.setStatus(ExpenseStatus.SUBMITTED);

        expense = expenseRepository.save(expense);

        auditLogService.log(expense.getId(), auditAction, userId,
                auditAction == AuditAction.RESUBMITTED ? "Expense resubmitted" : "Expense submitted",
                oldStatus, ExpenseStatus.SUBMITTED);

        return ExpenseDto.from(expense);
    }

    @Transactional
    public void deleteExpense(UUID expenseId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();

        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));

        // Assert current user is submitter
        if (!expense.getSubmitterId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the submitter can delete this expense");
        }

        // Assert status is DRAFT
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT expenses can be deleted");
        }

        // Hard delete (receipts will be handled by M8)
        expenseRepository.delete(expense);
    }
}
