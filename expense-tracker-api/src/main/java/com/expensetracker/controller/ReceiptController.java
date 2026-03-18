package com.expensetracker.controller;

import com.expensetracker.dto.response.ReceiptDto;
import com.expensetracker.exception.BusinessRuleException;
import com.expensetracker.exception.ConflictException;
import com.expensetracker.exception.ForbiddenException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseReceipt;
import com.expensetracker.model.enums.ExpenseStatus;
import com.expensetracker.repository.ExpenseReceiptRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.security.SecurityUtils;
import com.expensetracker.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses/{expenseId}/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_RECEIPTS_PER_EXPENSE = 3;

    private final ExpenseRepository expenseRepository;
    private final ExpenseReceiptRepository receiptRepository;
    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseEntity<ReceiptDto> uploadReceipt(
            @PathVariable UUID expenseId,
            @RequestParam("file") MultipartFile file) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId.toString()));

        // Assert current user is submitter
        if (!expense.getSubmitterId().equals(currentUserId)) {
            throw new ForbiddenException("Only the expense submitter can upload receipts");
        }

        // Assert expense is DRAFT or REJECTED
        if (expense.getStatus() != ExpenseStatus.DRAFT && expense.getStatus() != ExpenseStatus.REJECTED) {
            throw new ConflictException("Receipts can only be uploaded to DRAFT or REJECTED expenses",
                    "INVALID_EXPENSE_STATUS");
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException(
                    "Invalid file type. Allowed types: JPEG, PNG, PDF",
                    "INVALID_FILE_TYPE");
        }

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessRuleException(
                    "File size exceeds the maximum allowed size of 5MB",
                    "FILE_TOO_LARGE");
        }

        // Check receipt count
        long currentCount = receiptRepository.countByExpenseId(expenseId);
        if (currentCount >= MAX_RECEIPTS_PER_EXPENSE) {
            throw new ConflictException(
                    "Maximum number of receipts (" + MAX_RECEIPTS_PER_EXPENSE + ") already reached",
                    "MAX_RECEIPTS_REACHED");
        }

        // Store file
        String storagePath = fileStorageService.store(tenantId, expenseId, file);

        // Create receipt record
        ExpenseReceipt receipt = ExpenseReceipt.builder()
                .expense(expense)
                .fileName(file.getOriginalFilename())
                .filePath(storagePath)
                .contentType(contentType)
                .fileSizeBytes(file.getSize())
                .build();

        ExpenseReceipt saved = receiptRepository.save(receipt);

        return ResponseEntity.status(HttpStatus.CREATED).body(toReceiptDto(saved));
    }

    @GetMapping("/{receiptId}")
    public ResponseEntity<Resource> downloadReceipt(
            @PathVariable UUID expenseId,
            @PathVariable UUID receiptId) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId.toString()));

        // Access check: submitter OR assigned manager OR admin
        boolean isSubmitter = expense.getSubmitterId().equals(currentUserId);
        boolean isManager = expense.getManagerId() != null && expense.getManagerId().equals(currentUserId);
        boolean isAdmin = SecurityUtils.isAdmin();

        if (!isSubmitter && !isManager && !isAdmin) {
            throw new ForbiddenException("You do not have access to this receipt");
        }

        ExpenseReceipt receipt = receiptRepository.findByIdAndExpenseId(receiptId, expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId.toString()));

        Resource resource = fileStorageService.load(receipt.getFilePath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(receipt.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + receipt.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{receiptId}")
    public ResponseEntity<Void> deleteReceipt(
            @PathVariable UUID expenseId,
            @PathVariable UUID receiptId) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Expense expense = expenseRepository.findByIdAndTenantId(expenseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId.toString()));

        // Assert current user is submitter
        if (!expense.getSubmitterId().equals(currentUserId)) {
            throw new ForbiddenException("Only the expense submitter can delete receipts");
        }

        // Assert expense is DRAFT
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new ConflictException("Receipts can only be deleted from DRAFT expenses",
                    "INVALID_EXPENSE_STATUS");
        }

        ExpenseReceipt receipt = receiptRepository.findByIdAndExpenseId(receiptId, expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId.toString()));

        // Delete file from filesystem
        fileStorageService.delete(receipt.getFilePath());

        // Delete receipt record
        receiptRepository.delete(receipt);

        return ResponseEntity.noContent().build();
    }

    private ReceiptDto toReceiptDto(ExpenseReceipt receipt) {
        return ReceiptDto.builder()
                .id(receipt.getId())
                .fileName(receipt.getFileName())
                .contentType(receipt.getContentType())
                .fileSize(receipt.getFileSizeBytes())
                .createdAt(receipt.getCreatedAt())
                .build();
    }
}
