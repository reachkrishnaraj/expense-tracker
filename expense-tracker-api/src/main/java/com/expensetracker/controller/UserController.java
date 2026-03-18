package com.expensetracker.controller;

import com.expensetracker.dto.request.AssignManagerRequest;
import com.expensetracker.dto.request.ChangeRoleRequest;
import com.expensetracker.dto.response.UserDto;
import com.expensetracker.model.enums.Role;
import com.expensetracker.security.SecurityUtils;
import com.expensetracker.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<UserDto>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<UserDto> users = userService.listUsers(tenantId, role, search, pageable);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserDto> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Role newRole;
        try {
            newRole = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.expensetracker.exception.BusinessRuleException(
                    "Invalid role: " + request.getRole(), "INVALID_ROLE");
        }
        UserDto updated = userService.changeRole(tenantId, id, newRole);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/manager")
    public ResponseEntity<UserDto> assignManager(
            @PathVariable UUID id,
            @Valid @RequestBody AssignManagerRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserDto updated = userService.assignManager(tenantId, id, request.getManagerId());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<UserDto> deactivateUser(@PathVariable UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        UserDto updated = userService.deactivateUser(tenantId, id, currentUserId);
        return ResponseEntity.ok(updated);
    }
}
