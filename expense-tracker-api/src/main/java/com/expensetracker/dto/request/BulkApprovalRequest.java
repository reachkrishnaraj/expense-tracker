package com.expensetracker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkApprovalRequest {

    @NotNull(message = "Action is required")
    @Pattern(regexp = "APPROVE|REJECT", message = "Action must be APPROVE or REJECT")
    private String action;

    @NotEmpty(message = "At least one expense ID is required")
    @Size(max = 50, message = "Maximum 50 expense IDs allowed per bulk operation")
    private List<UUID> expenseIds;

    private String comment;
}
