package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public record AdvancedAnalyticsResponse(
    EconomicKpis economics,
    CustomerKpis customers,
    AttendanceKpis attendance,
    PaymentKpis payments,
    Forecast forecast,
    List<MonthlyTrend> monthlyTrend,
    List<Insight> insights
) {
    public record EconomicKpis(
        BigDecimal revenue,
        BigDecimal expenses,
        BigDecimal profit,
        BigDecimal previousMonthRevenue,
        BigDecimal revenueGrowthPercent
    ) {}

    public record CustomerKpis(
        long active,
        long inactive,
        long newMembers,
        long renewals,
        long lostMembers
    ) {}

    public record AttendanceKpis(
        String peakHour,
        DayOfWeek busiestDay,
        BigDecimal averageVisitsPerMemberPerWeek,
        long visitsThisWeek,
        BigDecimal weeklyChangePercent
    ) {}

    public record PaymentKpis(
        Map<PaymentMethod, BigDecimal> amountByMethod,
        Map<PaymentMethod, BigDecimal> percentageByMethod
    ) {}

    public record Forecast(
        long subscriptionsExpiringIn7Days,
        BigDecimal renewalValueAtRisk,
        BigDecimal expectedRevenueNextMonth
    ) {}

    public record MonthlyTrend(
        YearMonth month,
        BigDecimal revenue,
        BigDecimal expenses,
        BigDecimal profit
    ) {}

    public record Insight(
        Severity severity,
        String code,
        String message,
        BigDecimal changePercent
    ) {}

    public enum Severity { POSITIVE, INFO, WARNING }
}
