package com.example.expensetracker.repository;

import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.Expense;
import com.example.expensetracker.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUser(User user);

    List<Expense> findByUserId(Long userId);

    List<Expense> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    Page<Expense> findByUser(User user, Pageable pageable);

    Page<Expense> findByUserId(Long userId, Pageable pageable);

    Page<Expense> findByUserAndCategory(User user, Category category, Pageable pageable);

    Page<Expense> findByUserIdAndCategory(Long userId, Category category, Pageable pageable);

    Page<Expense> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Expense> findByUserIdAndCategoryAndDateBetween(Long userId, Category category, LocalDate startDate, LocalDate endDate, Pageable pageable);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.user.id = :userId AND e.date BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByUserIdAndDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.user.id = :userId AND e.date BETWEEN :startDate AND :endDate GROUP BY e.category")
    List<Object[]> sumAmountGroupByCategoryAndDateBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Checks if any expense row exists for the given user ID, including soft-deleted expenses.
     * Uses a native EXISTS query on the expenses table to bypass Hibernate's @SQLRestriction("is_deleted = false").
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM expenses WHERE user_id = :userId)", nativeQuery = true)
    boolean existsByUserIdIncludingDeleted(@Param("userId") Long userId);
}