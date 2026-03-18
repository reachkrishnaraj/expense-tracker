package com.expensetracker.service;

import com.expensetracker.dto.response.AnalyticsSummaryDto;
import com.expensetracker.dto.response.CategorySpendDto;
import com.expensetracker.dto.response.MonthlySpendDto;
import com.expensetracker.dto.response.TeamSpendDto;
import com.expensetracker.model.enums.ExpenseStatus;
import com.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getSummary(UUID tenantId, LocalDate from, LocalDate to) {
        List<Object[]> results = expenseRepository.countAndSumByStatusDateRange(tenantId, from, to);

        long totalSubmitted = 0;
        long totalApproved = 0;
        long totalRejected = 0;
        long totalPending = 0;
        BigDecimal totalApprovedAmount = BigDecimal.ZERO;

        for (Object[] row : results) {
            ExpenseStatus status = (ExpenseStatus) row[0];
            long count = (Long) row[1];
            BigDecimal sum = (BigDecimal) row[2];

            switch (status) {
                case SUBMITTED -> totalPending = count;
                case APPROVED -> {
                    totalApproved = count;
                    totalApprovedAmount = sum;
                }
                case REJECTED -> totalRejected = count;
                default -> { /* ignore DRAFT, CANCELLED */ }
            }
            totalSubmitted += count;
        }

        return AnalyticsSummaryDto.builder()
                .totalSubmitted(totalSubmitted)
                .totalApproved(totalApproved)
                .totalRejected(totalRejected)
                .totalPending(totalPending)
                .totalApprovedAmount(totalApprovedAmount)
                .currency("USD")
                .build();
    }

    @Transactional(readOnly = true)
    public List<CategorySpendDto> getSpendByCategory(UUID tenantId, LocalDate from, LocalDate to) {
        List<Object[]> results = expenseRepository.findSpendByCategory(tenantId, from, to);
        List<CategorySpendDto> dtos = new ArrayList<>();

        for (Object[] row : results) {
            dtos.add(CategorySpendDto.builder()
                    .categoryName((String) row[0])
                    .totalAmount((BigDecimal) row[1])
                    .expenseCount((Long) row[2])
                    .build());
        }

        return dtos;
    }

    @Transactional(readOnly = true)
    public List<MonthlySpendDto> getSpendByMonth(UUID tenantId, int months) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(months);

        List<Object[]> results = expenseRepository.findSpendByMonth(tenantId, from, to);
        List<MonthlySpendDto> dtos = new ArrayList<>();

        for (Object[] row : results) {
            dtos.add(MonthlySpendDto.builder()
                    .month((String) row[0])
                    .totalAmount((BigDecimal) row[1])
                    .expenseCount(((Number) row[2]).longValue())
                    .build());
        }

        return dtos;
    }

    @Transactional(readOnly = true)
    public List<TeamSpendDto> getSpendByTeam(UUID tenantId, LocalDate from, LocalDate to) {
        List<Object[]> results = expenseRepository.findSpendByTeam(tenantId, from, to);
        List<TeamSpendDto> dtos = new ArrayList<>();

        for (Object[] row : results) {
            String firstName = (String) row[0];
            String lastName = (String) row[1];
            dtos.add(TeamSpendDto.builder()
                    .managerName(firstName + " " + lastName)
                    .totalAmount((BigDecimal) row[2])
                    .expenseCount((Long) row[3])
                    .build());
        }

        return dtos;
    }

    @Transactional(readOnly = true)
    public List<CategorySpendDto> getMyTeamAnalytics(UUID tenantId, UUID managerId,
                                                      LocalDate from, LocalDate to) {
        List<Object[]> results = expenseRepository.findSpendByCategoryForManager(
                tenantId, managerId, from, to);
        List<CategorySpendDto> dtos = new ArrayList<>();

        for (Object[] row : results) {
            dtos.add(CategorySpendDto.builder()
                    .categoryName((String) row[0])
                    .totalAmount((BigDecimal) row[1])
                    .expenseCount((Long) row[2])
                    .build());
        }

        return dtos;
    }
}
