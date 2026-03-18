package com.expensetracker.controller;

import com.expensetracker.dto.request.CreateExpenseRequest;
import com.expensetracker.dto.request.UpdateExpenseRequest;
import com.expensetracker.dto.response.ExpenseDetailDto;
import com.expensetracker.dto.response.ExpenseDto;
import com.expensetracker.dto.response.ExpenseSummaryDto;
import com.expensetracker.model.enums.ExpenseStatus;
import com.expensetracker.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")
    public ResponseEntity<ExpenseDto> createExpense(@RequestBody CreateExpenseRequest request) {
        ExpenseDto expense = expenseService.createExpense(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(expense);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")
    public ResponseEntity<ExpenseDto> updateExpense(@PathVariable UUID id,
                                                     @RequestBody UpdateExpenseRequest request) {
        ExpenseDto expense = expenseService.updateExpense(id, request);
        return ResponseEntity.ok(expense);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseDetailDto> getExpense(@PathVariable UUID id) {
        ExpenseDetailDto expense = expenseService.getExpense(id);
        return ResponseEntity.ok(expense);
    }

    @GetMapping
    public ResponseEntity<Page<ExpenseSummaryDto>> listExpenses(
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int effectiveSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(page, effectiveSize);
        Page<ExpenseSummaryDto> expenses = expenseService.listExpenses(
                null, status, categoryId, fromDate, toDate, pageable);
        return ResponseEntity.ok(expenses);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")
    public ResponseEntity<ExpenseDto> submitExpense(@PathVariable UUID id) {
        ExpenseDto expense = expenseService.submitExpense(id);
        return ResponseEntity.ok(expense);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")
    public ResponseEntity<Void> deleteExpense(@PathVariable UUID id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }
}
