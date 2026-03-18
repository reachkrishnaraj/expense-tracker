package com.expensetracker.repository;

import com.expensetracker.model.User;
import com.expensetracker.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId " +
           "AND (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchByTenantId(@Param("tenantId") UUID tenantId,
                                 @Param("search") String search,
                                 Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND u.role = :role " +
           "AND (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchByTenantIdAndRole(@Param("tenantId") UUID tenantId,
                                        @Param("role") Role role,
                                        @Param("search") String search,
                                        Pageable pageable);
}
