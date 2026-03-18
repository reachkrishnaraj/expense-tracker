package com.expensetracker.dto.response;

import com.expensetracker.model.ExpenseAuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {

    private String action;
    private String performedBy;
    private String comment;
    private String oldStatus;
    private String newStatus;
    private LocalDateTime createdAt;

    public static AuditLogDto from(ExpenseAuditLog log) {
        if (log == null) {
            return null;
        }
        String performerName = null;
        if (log.getPerformedBy() != null) {
            performerName = log.getPerformedBy().getFirstName() + " " + log.getPerformedBy().getLastName();
        }
        return AuditLogDto.builder()
                .action(log.getAction() != null ? log.getAction().name() : null)
                .performedBy(performerName)
                .comment(log.getComment())
                .oldStatus(log.getOldStatus())
                .newStatus(log.getNewStatus())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
