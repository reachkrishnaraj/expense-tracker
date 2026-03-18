package com.expensetracker.dto.response;

import com.expensetracker.model.Expense;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExpenseDetailDto extends ExpenseDto {

    private UserInfo manager;
    private UserInfo approvedBy;
    private LocalDateTime approvedAt;
    private String rejectionComment;
    private List<ReceiptDto> receipts;
    private List<AuditLogDto> auditTrail;

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    @lombok.Builder
    public static class UserInfo {
        private UUID id;
        private String name;
    }

    public static ExpenseDetailDto from(Expense expense, List<ReceiptDto> receipts, List<AuditLogDto> auditTrail) {
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

        UserInfo managerInfo = null;
        if (expense.getManager() != null) {
            managerInfo = UserInfo.builder()
                    .id(expense.getManager().getId())
                    .name(expense.getManager().getFirstName() + " " + expense.getManager().getLastName())
                    .build();
        }

        UserInfo approvedByInfo = null;
        if (expense.getApprovedBy() != null) {
            approvedByInfo = UserInfo.builder()
                    .id(expense.getApprovedBy().getId())
                    .name(expense.getApprovedBy().getFirstName() + " " + expense.getApprovedBy().getLastName())
                    .build();
        }

        return ExpenseDetailDto.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .category(categoryDto)
                .merchantName(expense.getMerchantName())
                .expenseDate(expense.getExpenseDate())
                .notes(expense.getNotes())
                .status(expense.getStatus() != null ? expense.getStatus().name() : null)
                .submitter(submitterInfo)
                .receiptCount(receipts != null ? receipts.size() : 0)
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .manager(managerInfo)
                .approvedBy(approvedByInfo)
                .approvedAt(expense.getApprovedAt())
                .rejectionComment(expense.getRejectionComment())
                .receipts(receipts)
                .auditTrail(auditTrail)
                .build();
    }
}
