package com.expensetracker.dto.response;

import com.expensetracker.model.Expense;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExpenseDto {

    private UUID id;
    private BigDecimal amount;
    private String currency;
    private CategoryDto category;
    private String merchantName;
    private LocalDate expenseDate;
    private String notes;
    private String status;
    private SubmitterInfo submitter;
    private int receiptCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubmitterInfo {
        private UUID id;
        private String name;
    }

    public static ExpenseDto from(Expense expense) {
        if (expense == null) {
            return null;
        }

        SubmitterInfo submitterInfo = null;
        if (expense.getSubmitter() != null) {
            submitterInfo = SubmitterInfo.builder()
                    .id(expense.getSubmitter().getId())
                    .name(expense.getSubmitter().getFirstName() + " " + expense.getSubmitter().getLastName())
                    .build();
        }

        CategoryDto categoryDto = null;
        if (expense.getCategory() != null) {
            categoryDto = CategoryDto.from(expense.getCategory());
        }

        return ExpenseDto.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .category(categoryDto)
                .merchantName(expense.getMerchantName())
                .expenseDate(expense.getExpenseDate())
                .notes(expense.getNotes())
                .status(expense.getStatus() != null ? expense.getStatus().name() : null)
                .submitter(submitterInfo)
                .receiptCount(0) // Will be set by service if receipts exist
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
}
