package com.minegolem.backend.service;

import com.minegolem.backend.domain.entity.Access;
import com.minegolem.backend.domain.entity.Payment;
import com.minegolem.backend.domain.entity.Subscription;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.domain.enums.PaymentMethod;
import com.minegolem.backend.domain.enums.PaymentType;
import com.minegolem.backend.dto.response.AdvancedAnalyticsResponse;
import com.minegolem.backend.repository.AccessRepository;
import com.minegolem.backend.repository.PaymentRepository;
import com.minegolem.backend.repository.SubscriptionRepository;
import com.minegolem.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static com.minegolem.backend.dto.response.AdvancedAnalyticsResponse.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdvancedAnalyticsService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final UserRepository userRepository;
    private final AccessRepository accessRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    public AdvancedAnalyticsResponse calculate(UUID gymId, LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate sixMonthsAgo = currentMonth.minusMonths(5).atDay(1);
        LocalDate nextMonthEnd = currentMonth.plusMonths(1).atEndOfMonth();
        LocalDateTime accessFrom = today.minusDays(27).atStartOfDay();
        LocalDateTime accessTo = today.plusDays(1).atStartOfDay().minusNanos(1);

        List<User> users = userRepository.findByGymIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(gymId);
        List<Payment> payments = paymentRepository.findByGymAndDateRange(gymId, sixMonthsAgo, today);
        List<Subscription> subscriptions = subscriptionRepository.findByGymAndDateOverlap(
            gymId, sixMonthsAgo.minusYears(2), nextMonthEnd
        );
        List<Access> accesses = accessRepository.findByGymAndTimeRange(gymId, accessFrom, accessTo);

        EconomicKpis economics = economics(payments, currentMonth);
        CustomerKpis customers = customers(users, subscriptions, today, currentMonth);
        AttendanceKpis attendance = attendance(accesses, customers.active(), today);
        PaymentKpis paymentKpis = paymentMethods(payments, currentMonth);
        Forecast forecast = forecast(subscriptions, payments, today, currentMonth);
        List<MonthlyTrend> trend = monthlyTrend(payments, currentMonth);
        List<Insight> insights = insights(economics, customers, attendance, paymentKpis, forecast);

        return new AdvancedAnalyticsResponse(
            economics, customers, attendance, paymentKpis, forecast, trend, insights
        );
    }

    private EconomicKpis economics(List<Payment> payments, YearMonth month) {
        BigDecimal revenue = total(payments, p -> inMonth(p, month) && p.getType() == PaymentType.INCOME);
        BigDecimal expenses = total(payments, p -> inMonth(p, month) && p.getType() == PaymentType.EXPENSE);
        BigDecimal previous = total(payments, p -> inMonth(p, month.minusMonths(1)) && p.getType() == PaymentType.INCOME);
        return new EconomicKpis(revenue, expenses, revenue.subtract(expenses), previous, change(revenue, previous));
    }

    private CustomerKpis customers(List<User> users, List<Subscription> subscriptions,
                                   LocalDate today, YearMonth month) {
        Set<UUID> activeIds = new HashSet<>();
        for (Subscription s : subscriptions) {
            if (!s.getStartDate().isAfter(today) && !s.getEndDate().isBefore(today)) {
                activeIds.add(s.getUser().getId());
            }
        }

        long newMembers = users.stream()
            .filter(u -> u.getCreatedAt() != null && YearMonth.from(u.getCreatedAt()).equals(month))
            .count();

        Map<UUID, List<Subscription>> byUser = new HashMap<>();
        subscriptions.forEach(s -> byUser.computeIfAbsent(s.getUser().getId(), ignored -> new ArrayList<>()).add(s));
        long renewals = byUser.values().stream().filter(list -> list.stream()
            .filter(s -> YearMonth.from(s.getStartDate()).equals(month))
            .anyMatch(current -> list.stream().anyMatch(previous -> previous != current
                && previous.getStartDate().isBefore(current.getStartDate())))).count();

        LocalDate previousMonthStart = month.minusMonths(1).atDay(1);
        LocalDate previousMonthEnd = month.minusMonths(1).atEndOfMonth();
        long lost = byUser.entrySet().stream().filter(entry -> {
            LocalDate lastEnd = entry.getValue().stream().map(Subscription::getEndDate).max(LocalDate::compareTo).orElse(LocalDate.MIN);
            return !lastEnd.isBefore(previousMonthStart) && !lastEnd.isAfter(previousMonthEnd)
                && !activeIds.contains(entry.getKey());
        }).count();

        long active = activeIds.size();
        return new CustomerKpis(active, Math.max(0, users.size() - active), newMembers, renewals, lost);
    }

    private AttendanceKpis attendance(List<Access> accesses, long activeMembers, LocalDate today) {
        List<Access> granted = accesses.stream().filter(Access::isGranted).toList();
        Map<Integer, Long> byHour = new HashMap<>();
        Map<DayOfWeek, Long> byDay = new EnumMap<>(DayOfWeek.class);
        granted.forEach(a -> {
            byHour.merge(a.getAccessTime().getHour(), 1L, Long::sum);
            byDay.merge(a.getAccessTime().getDayOfWeek(), 1L, Long::sum);
        });
        int peak = byHour.entrySet().stream().max(Map.Entry.<Integer, Long>comparingByValue()
            .thenComparing(Map.Entry.comparingByKey())).map(Map.Entry::getKey).orElse(0);
        DayOfWeek busiest = byDay.entrySet().stream().max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse(null);

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate previousWeekStart = weekStart.minusWeeks(1);
        long thisWeek = granted.stream().filter(a -> !a.getAccessTime().toLocalDate().isBefore(weekStart)).count();
        long previousWeek = granted.stream().filter(a -> {
            LocalDate date = a.getAccessTime().toLocalDate();
            return !date.isBefore(previousWeekStart) && date.isBefore(weekStart);
        }).count();
        BigDecimal average = activeMembers == 0 ? BigDecimal.ZERO
            : BigDecimal.valueOf(granted.size()).divide(BigDecimal.valueOf(4L * activeMembers), 1, RoundingMode.HALF_UP);

        return new AttendanceKpis(
            String.format("%02d:00–%02d:00", peak, (peak + 1) % 24), busiest, average,
            thisWeek, change(BigDecimal.valueOf(thisWeek), BigDecimal.valueOf(previousWeek))
        );
    }

    private PaymentKpis paymentMethods(List<Payment> payments, YearMonth month) {
        Map<PaymentMethod, BigDecimal> amounts = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod method : PaymentMethod.values()) amounts.put(method, BigDecimal.ZERO);
        payments.stream().filter(p -> inMonth(p, month) && p.getType() == PaymentType.INCOME)
            .forEach(p -> amounts.merge(p.getMethod(), p.getAmount(), BigDecimal::add));
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<PaymentMethod, BigDecimal> percentages = new EnumMap<>(PaymentMethod.class);
        amounts.forEach((method, amount) -> percentages.put(method, total.signum() == 0 ? BigDecimal.ZERO
            : amount.multiply(ONE_HUNDRED).divide(total, 1, RoundingMode.HALF_UP)));
        return new PaymentKpis(amounts, percentages);
    }

    private Forecast forecast(List<Subscription> subscriptions, List<Payment> payments,
                              LocalDate today, YearMonth month) {
        List<Subscription> expiring = subscriptions.stream()
            .filter(s -> !s.getEndDate().isBefore(today) && !s.getEndDate().isAfter(today.plusDays(7)))
            .toList();
        BigDecimal risk = expiring.stream().map(Subscription::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate historyStart = today.minusDays(89);
        BigDecimal trailingRevenue = total(payments, p -> p.getType() == PaymentType.INCOME
            && !p.getPaymentDate().isBefore(historyStart) && !p.getPaymentDate().isAfter(today));
        long observedDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(historyStart, today) + 1);
        BigDecimal expected = trailingRevenue.divide(BigDecimal.valueOf(observedDays), 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(month.plusMonths(1).lengthOfMonth())).setScale(2, RoundingMode.HALF_UP);
        return new Forecast(expiring.size(), risk, expected);
    }

    private List<MonthlyTrend> monthlyTrend(List<Payment> payments, YearMonth current) {
        List<MonthlyTrend> result = new ArrayList<>();
        for (int offset = 5; offset >= 0; offset--) {
            YearMonth month = current.minusMonths(offset);
            BigDecimal revenue = total(payments, p -> inMonth(p, month) && p.getType() == PaymentType.INCOME);
            BigDecimal expenses = total(payments, p -> inMonth(p, month) && p.getType() == PaymentType.EXPENSE);
            result.add(new MonthlyTrend(month, revenue, expenses, revenue.subtract(expenses)));
        }
        return result;
    }

    private List<Insight> insights(EconomicKpis economics, CustomerKpis customers,
                                   AttendanceKpis attendance, PaymentKpis payments, Forecast forecast) {
        List<Insight> result = new ArrayList<>();
        if (economics.revenueGrowthPercent().signum() != 0) {
            boolean growing = economics.revenueGrowthPercent().signum() > 0;
            result.add(new Insight(growing ? Severity.POSITIVE : Severity.WARNING, "REVENUE_TREND",
                growing ? "Le entrate sono in crescita rispetto al mese scorso"
                    : "Le entrate sono diminuite rispetto al mese scorso", economics.revenueGrowthPercent().abs()));
        }
        if (attendance.weeklyChangePercent().abs().compareTo(BigDecimal.TEN) >= 0) {
            boolean growing = attendance.weeklyChangePercent().signum() > 0;
            result.add(new Insight(growing ? Severity.POSITIVE : Severity.WARNING, "ATTENDANCE_TREND",
                growing ? "Gli accessi sono aumentati questa settimana"
                    : "Gli accessi sono diminuiti questa settimana", attendance.weeklyChangePercent().abs()));
        }
        if (customers.lostMembers() > customers.newMembers()) {
            result.add(new Insight(Severity.WARNING, "CUSTOMER_CHURN",
                "I clienti persi superano le nuove iscrizioni", null));
        }
        if (forecast.subscriptionsExpiringIn7Days() > 0) {
            result.add(new Insight(Severity.INFO, "RENEWALS_DUE",
                forecast.subscriptionsExpiringIn7Days() + " abbonamenti scadranno nei prossimi 7 giorni", null));
        }
        PaymentMethod dominant = payments.percentageByMethod().entrySet().stream()
            .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (dominant != null && payments.percentageByMethod().get(dominant).compareTo(BigDecimal.valueOf(60)) >= 0) {
            result.add(new Insight(Severity.INFO, "PAYMENT_CONCENTRATION",
                "Il metodo di pagamento prevalente è " + dominant, payments.percentageByMethod().get(dominant)));
        }
        if (result.isEmpty()) result.add(new Insight(Severity.INFO, "STABLE_BUSINESS",
            "Gli indicatori principali sono stabili", null));
        return result;
    }

    private static boolean inMonth(Payment payment, YearMonth month) {
        return YearMonth.from(payment.getPaymentDate()).equals(month);
    }

    private static BigDecimal total(List<Payment> payments, Predicate<Payment> predicate) {
        return payments.stream().filter(predicate).map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal change(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return current.signum() == 0 ? BigDecimal.ZERO : ONE_HUNDRED;
        return current.subtract(previous).multiply(ONE_HUNDRED)
            .divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }
}
