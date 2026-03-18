package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import com.expensetracker.model.enums.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Optional<Expense> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Expense> findByTenantIdAndSubmitterId(UUID tenantId, UUID submitterId, Pageable pageable);

    Page<Expense> findByTenantIdAndManagerIdAndStatus(UUID tenantId, UUID managerId,
                                                      ExpenseStatus status, Pageable pageable);

    Page<Expense> findByTenantIdAndStatus(UUID tenantId, ExpenseStatus status, Pageable pageable);

    List<Expense> findByTenantIdAndManagerIdAndStatusIn(UUID tenantId, UUID managerId,
                                                        Collection<ExpenseStatus> statuses);

    // Count and sum grouped by status for a given tenant
    @Query("SELECT e.status, COUNT(e), COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.tenantId = :tenantId GROUP BY e.status")
    List<Object[]> countAndSumByStatus(@Param("tenantId") UUID tenantId);

    // Sum by category for approved expenses within a date range
    @Query("SELECT e.category.name, SUM(e.amount), COUNT(e) FROM Expense e " +
           "WHERE e.tenantId = :tenantId AND e.status = 'APPROVED' " +
           "AND e.expenseDate BETWEEN :from AND :to GROUP BY e.category.name")
    List<Object[]> findSpendByCategory(@Param("tenantId") UUID tenantId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    // Sum by month for approved expenses (native query for TO_CHAR)
    @Query(value = "SELECT TO_CHAR(e.expense_date, 'YYYY-MM') AS month, " +
                   "SUM(e.amount) AS total, COUNT(*) AS count " +
                   "FROM expenses e " +
                   "WHERE e.tenant_id = :tenantId AND e.status = 'APPROVED' " +
                   "AND e.expense_date BETWEEN :from AND :to " +
                   "GROUP BY TO_CHAR(e.expense_date, 'YYYY-MM') " +
                   "ORDER BY month",
           nativeQuery = true)
    List<Object[]> findSpendByMonth(@Param("tenantId") UUID tenantId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    // Sum by team (manager) for approved expenses within a date range
    @Query("SELECT e.manager.firstName, e.manager.lastName, SUM(e.amount), COUNT(e) " +
           "FROM Expense e " +
           "WHERE e.tenantId = :tenantId AND e.status = 'APPROVED' " +
           "AND e.expenseDate BETWEEN :from AND :to " +
           "GROUP BY e.manager.id, e.manager.firstName, e.manager.lastName")
    List<Object[]> findSpendByTeam(@Param("tenantId") UUID tenantId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    // Bulk reassignment of submitted expenses from one manager to another
    @Modifying
    @Query("UPDATE Expense e SET e.manager = :newManager " +
           "WHERE e.tenantId = :tenantId AND e.manager.id = :oldManagerId " +
           "AND e.status = 'SUBMITTED'")
    int updateManagerIdForSubmittedExpenses(@Param("tenantId") UUID tenantId,
                                           @Param("oldManagerId") UUID oldManagerId,
                                           @Param("newManager") com.expensetracker.model.User newManager);
}
