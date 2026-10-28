package com.hostelmanagement.expenses;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("""
            SELECT e FROM Expense e WHERE e.hostelId IN :ids AND (:category IS NULL OR e.category = :category)
            AND (:from IS NULL OR e.expenseDate >= :from) AND (:to IS NULL OR e.expenseDate <= :to)""")
    Page<Expense> search(@Param("ids") Collection<Long> ids, @Param("category") String category,
                         @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);
}
