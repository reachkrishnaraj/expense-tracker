package com.expensetracker.service;

import com.expensetracker.dto.response.UserDto;
import com.expensetracker.exception.BusinessRuleException;
import com.expensetracker.exception.ConflictException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.model.enums.AuditAction;
import com.expensetracker.model.enums.ExpenseStatus;
import com.expensetracker.model.enums.Role;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.RefreshTokenRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<UserDto> listUsers(UUID tenantId, String roleFilter, String search, Pageable pageable) {
        Page<User> users;

        Role role = null;
        if (roleFilter != null && !roleFilter.isBlank()) {
            try {
                role = Role.valueOf(roleFilter.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid role filter: " + roleFilter, "INVALID_ROLE");
            }
        }

        if (search != null && !search.isBlank()) {
            if (role != null) {
                users = userRepository.searchByTenantIdAndRole(tenantId, role, search, pageable);
            } else {
                users = userRepository.searchByTenantId(tenantId, search, pageable);
            }
        } else {
            if (role != null) {
                users = userRepository.findByTenantIdAndRole(tenantId, role, pageable);
            } else {
                users = userRepository.findByTenantId(tenantId, pageable);
            }
        }

        return users.map(this::toUserDto);
    }

    @Transactional
    public UserDto changeRole(UUID tenantId, UUID userId, Role newRole) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        // If changing FROM Manager, check no active reports
        if (user.getRole() == Role.MANAGER && newRole != Role.MANAGER) {
            long reportCount = userRepository.countByTenantIdAndManagerIdAndIsActiveTrue(tenantId, userId);
            if (reportCount > 0) {
                throw new ConflictException("Reassign employees before changing this user's role",
                        "MANAGER_HAS_REPORTS");
            }
        }

        user.setRole(newRole);
        User saved = userRepository.save(user);
        log.info("Changed role for user {} to {} in tenant {}", userId, newRole, tenantId);
        return toUserDto(saved);
    }

    @Transactional
    public UserDto assignManager(UUID tenantId, UUID userId, UUID managerId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        User newManager = userRepository.findByIdAndTenantId(managerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager", managerId.toString()));

        if (newManager.getRole() != Role.MANAGER && newManager.getRole() != Role.ADMIN) {
            throw new BusinessRuleException("Assigned manager must have MANAGER or ADMIN role",
                    "INVALID_MANAGER_ROLE");
        }

        UUID oldManagerId = user.getManagerId();
        user.setManager(newManager);
        User saved = userRepository.save(user);

        // Reassign all SUBMITTED expenses from old manager to new manager
        if (oldManagerId != null) {
            List<Expense> submittedExpenses = expenseRepository
                    .findByTenantIdAndManagerIdAndStatusIn(tenantId, oldManagerId,
                            List.of(ExpenseStatus.SUBMITTED));

            // Filter only expenses belonging to this specific user
            for (Expense expense : submittedExpenses) {
                if (expense.getSubmitterId().equals(userId)) {
                    expense.setManager(newManager);
                    expenseRepository.save(expense);

                    // Create audit log for reassignment
                    auditLogService.log(
                            expense.getId(),
                            AuditAction.REASSIGNED,
                            managerId, // performed by the new manager assignment action
                            "Manager reassigned from " + oldManagerId + " to " + managerId,
                            expense.getStatus(),
                            expense.getStatus()
                    );
                }
            }
        }

        log.info("Assigned manager {} to user {} in tenant {}", managerId, userId, tenantId);
        return toUserDto(saved);
    }

    @Transactional
    public UserDto deactivateUser(UUID tenantId, UUID userId, UUID performedByUserId) {
        // Cannot deactivate self
        if (userId.equals(performedByUserId)) {
            throw new ConflictException("Cannot deactivate your own account", "SELF_DEACTIVATION");
        }

        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));

        // If Manager with active reports, throw 409
        if (user.getRole() == Role.MANAGER) {
            long reportCount = userRepository.countByTenantIdAndManagerIdAndIsActiveTrue(tenantId, userId);
            if (reportCount > 0) {
                throw new ConflictException("Reassign employees before deactivating this manager",
                        "MANAGER_HAS_REPORTS");
            }
        }

        // Set inactive
        user.setIsActive(false);
        userRepository.save(user);

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllByUserId(userId);

        // Cancel all SUBMITTED and DRAFT expenses
        List<Expense> expensesToCancel = expenseRepository
                .findByTenantIdAndSubmitterIdAndStatusIn(tenantId, userId,
                        List.of(ExpenseStatus.SUBMITTED, ExpenseStatus.DRAFT));

        for (Expense expense : expensesToCancel) {
            ExpenseStatus oldStatus = expense.getStatus();
            expense.setStatus(ExpenseStatus.CANCELLED);
            expenseRepository.save(expense);

            auditLogService.log(
                    expense.getId(),
                    AuditAction.CANCELLED,
                    performedByUserId,
                    "User deactivated by admin",
                    oldStatus,
                    ExpenseStatus.CANCELLED
            );
        }

        log.info("Deactivated user {} in tenant {} by admin {}", userId, tenantId, performedByUserId);
        return toUserDto(user);
    }

    private UserDto toUserDto(User user) {
        String managerName = null;
        if (user.getManager() != null) {
            managerName = user.getManager().getFirstName() + " " + user.getManager().getLastName();
        }

        String orgName = null;
        if (user.getOrganization() != null) {
            orgName = user.getOrganization().getName();
        }

        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .organizationId(user.getTenantId())
                .organizationName(orgName)
                .managerId(user.getManagerId())
                .managerName(managerName)
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
