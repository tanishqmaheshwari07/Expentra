package com.example.expensetracker.service;

import com.example.expensetracker.dto.ExpenseRequest;
import com.example.expensetracker.dto.ExpenseResponse;
import com.example.expensetracker.dto.ExpenseSummaryResponse;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.Expense;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.exception.ExpenseNotFoundException;
import com.example.expensetracker.exception.ExpenseNotOwnedException;
import com.example.expensetracker.mapper.ExpenseMapper;
import com.example.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserService userService;

    public ExpenseResponse createExpense(ExpenseRequest request) {
        User currentUser = userService.getCurrentUser();

        Expense expense = Expense.builder()
                .amount(request.getAmount())
                .description(request.getDescription())
                .category(request.getCategory())
                .date(request.getDate())
                .user(currentUser)
                .isDeleted(false)
                .build();

        Expense savedExpense = expenseRepository.save(expense);
        log.info("User ID: {} created expense ID: {}", currentUser.getId(), savedExpense.getId());
        return ExpenseMapper.toResponse(savedExpense);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseById(Long id) {
        User currentUser = userService.getCurrentUser();

        Expense expense = expenseRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new ExpenseNotFoundException(
                        "Expense with id " + id + " not found or does not belong to you"
                ));

        return ExpenseMapper.toResponse(expense);
    }

    public ExpenseResponse updateExpense(Long expenseId, ExpenseRequest request) {
        User currentUser = userService.getCurrentUser();

        Expense expense = expenseRepository.findByIdAndUserId(expenseId, currentUser.getId())
                .orElseThrow(() -> new ExpenseNotOwnedException(
                        "Expense with id " + expenseId + " does not belong to current user"
                ));

        expense.setAmount(request.getAmount());
        expense.setDescription(request.getDescription());
        expense.setCategory(request.getCategory());
        expense.setDate(request.getDate());

        Expense updatedExpense = expenseRepository.save(expense);
        log.info("User ID: {} updated expense ID: {}", currentUser.getId(), updatedExpense.getId());
        return ExpenseMapper.toResponse(updatedExpense);
    }

    public void deleteExpense(Long expenseId) {
        User currentUser = userService.getCurrentUser();

        Expense expense = expenseRepository.findByIdAndUserId(expenseId, currentUser.getId())
                .orElseThrow(() -> new ExpenseNotOwnedException(
                        "Expense with id " + expenseId + " does not belong to current user"
                ));

        expenseRepository.delete(expense);
        log.info("User ID: {} soft-deleted expense ID: {}", currentUser.getId(), expenseId);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getMyExpenses() {
        User currentUser = userService.getCurrentUser();
        List<Expense> expenses = expenseRepository.findByUserId(currentUser.getId());
        return expenses.stream()
                .map(ExpenseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> getExpenses(
            Category category,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        User currentUser = userService.getCurrentUser();
        Long userId = currentUser.getId();

        Page<Expense> page;

        if (category != null && startDate != null && endDate != null) {
            page = expenseRepository.findByUserIdAndCategoryAndDateBetween(userId, category, startDate, endDate, pageable);
        } else if (category != null) {
            page = expenseRepository.findByUserIdAndCategory(userId, category, pageable);
        } else if (startDate != null && endDate != null) {
            page = expenseRepository.findByUserIdAndDateBetween(userId, startDate, endDate, pageable);
        } else {
            page = expenseRepository.findByUserId(userId, pageable);
        }

        return page.map(ExpenseMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse getExpenseSummary(LocalDate startDate, LocalDate endDate) {
        User currentUser = userService.getCurrentUser();
        Long userId = currentUser.getId();

        LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.now();

        List<Expense> expenses = expenseRepository.findByUserIdAndDateBetween(userId, effectiveStart, effectiveEnd);

        BigDecimal total = BigDecimal.ZERO;
        Map<Category, BigDecimal> categoryMap = new EnumMap<>(Category.class);
        Map<String, BigDecimal> monthlyMap = new TreeMap<>();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (Expense exp : expenses) {
            total = total.add(exp.getAmount());
            categoryMap.merge(exp.getCategory(), exp.getAmount(), BigDecimal::add);

            String monthKey = exp.getDate().format(monthFormatter);
            monthlyMap.merge(monthKey, exp.getAmount(), BigDecimal::add);
        }

        return ExpenseSummaryResponse.builder()
                .totalAmount(total)
                .totalCount(expenses.size())
                .startDate(effectiveStart)
                .endDate(effectiveEnd)
                .categoryBreakdown(categoryMap)
                .monthlyBreakdown(monthlyMap)
                .build();
    }
}