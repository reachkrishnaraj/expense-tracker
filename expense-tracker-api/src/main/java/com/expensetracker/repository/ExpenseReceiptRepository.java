package com.expensetracker.repository;

import com.expensetracker.model.ExpenseReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseReceiptRepository extends JpaRepository<ExpenseReceipt, UUID> {

    List<ExpenseReceipt> findByExpenseId(UUID expenseId);

    Optional<ExpenseReceipt> findByIdAndExpenseId(UUID id, UUID expenseId);

    long countByExpenseId(UUID expenseId);
}
