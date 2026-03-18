package com.expensetracker.controller;

import com.expensetracker.dto.response.AnalyticsSummaryDto;
import com.expensetracker.dto.response.CategorySpendDto;
import com.expensetracker.dto.response.MonthlySpendDto;
import com.expensetracker.dto.response.TeamSpendDto;
import com.expensetracker.security.SecurityUtils;
import com.expensetracker.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AnalyticsSummaryDto> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        LocalDate[] range = resolveDefaultDateRange(fromDate, toDate);

        AnalyticsSummaryDto summary = analyticsService.getSummary(tenantId, range[0], range[1]);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/by-category")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CategorySpendDto>> getSpendByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        LocalDate[] range = resolveDefaultDateRange(fromDate, toDate);

        List<CategorySpendDto> result = analyticsService.getSpendByCategory(tenantId, range[0], range[1]);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-month")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MonthlySpendDto>> getSpendByMonth(
            @RequestParam(defaultValue = "6") int months) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        int effectiveMonths = Math.min(months, 12);

        List<MonthlySpendDto> result = analyticsService.getSpendByMonth(tenantId, effectiveMonths);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-team")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TeamSpendDto>> getSpendByTeam(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        LocalDate[] range = resolveDefaultDateRange(fromDate, toDate);

        List<TeamSpendDto> result = analyticsService.getSpendByTeam(tenantId, range[0], range[1]);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-team")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<CategorySpendDto>> getMyTeamAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        LocalDate[] range = resolveDefaultDateRange(fromDate, toDate);

        List<CategorySpendDto> result = analyticsService.getMyTeamAnalytics(
                tenantId, currentUserId, range[0], range[1]);
        return ResponseEntity.ok(result);
    }

    private LocalDate[] resolveDefaultDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate now = LocalDate.now();
        LocalDate from = fromDate != null ? fromDate : now.withDayOfMonth(1);
        LocalDate to = toDate != null ? toDate : now.withDayOfMonth(now.lengthOfMonth());
        return new LocalDate[]{from, to};
    }
}
