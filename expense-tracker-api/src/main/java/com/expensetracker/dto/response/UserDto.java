package com.expensetracker.dto.response;

import com.expensetracker.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private UUID organizationId;
    private String organizationName;
    private UUID managerId;
    private String managerName;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
