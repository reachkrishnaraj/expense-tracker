package com.expensetracker.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkApprovalResultDto {

    private int processed;
    private int skipped;
    private List<BulkResultItem> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkResultItem {
        private UUID expenseId;
        private String status; // SUCCESS or SKIPPED
        private String reason; // nullable, present when SKIPPED
    }
}
