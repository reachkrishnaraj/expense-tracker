package com.expensetracker.repository;

import com.expensetracker.model.User;
import com.expensetracker.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

    Page<User> findByTenantIdAndRole(UUID tenantId, Role role, Pageable pageable);

    List<User> findByTenantIdAndManagerId(UUID tenantId, UUID managerId);

    long countByTenantIdAndManagerIdAndIsActiveTrue(UUID tenantId, UUID managerId);
}
