package com.expensetracker.repository;

import com.expensetracker.model.ExpenseAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseAuditLogRepository extends JpaRepository<ExpenseAuditLog, UUID> {

    List<ExpenseAuditLog> findByExpenseIdOrderByCreatedAtAsc(UUID expenseId);
}
