package com.expensetracker.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignManagerRequest {

    @NotNull(message = "Manager ID is required")
    private UUID managerId;
}
