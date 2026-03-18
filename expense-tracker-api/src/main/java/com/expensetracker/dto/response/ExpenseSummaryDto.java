package com.expensetracker.dto.response;

import com.expensetracker.model.Expense;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseSummaryDto {

    private UUID id;
    private BigDecimal amount;
    private String currency;
    private String categoryName;
    private String merchantName;
    private LocalDate expenseDate;
    private String status;
    private LocalDateTime createdAt;
    private int receiptCount;

    public static ExpenseSummaryDto from(Expense expense) {
        if (expense == null) {
            return null;
        }
        return ExpenseSummaryDto.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .categoryName(expense.getCategory() != null ? expense.getCategory().getName() : null)
                .merchantName(expense.getMerchantName())
                .expenseDate(expense.getExpenseDate())
                .status(expense.getStatus() != null ? expense.getStatus().name() : null)
                .createdAt(expense.getCreatedAt())
                .receiptCount(0) // Will be set by service if receipts exist
                .build();
    }
}
