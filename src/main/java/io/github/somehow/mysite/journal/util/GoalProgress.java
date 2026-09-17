package io.github.somehow.mysite.journal.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GoalProgress {

    private GoalProgress() {
    }

    public static int percent(int actual, int target) {
        if (target <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(actual * 100.0 / target));
    }

    public static int suggestedQuota(int remaining, int slots) {
        if (remaining <= 0) {
            return 0;
        }
        if (slots <= 0) {
            return remaining;
        }
        return (int) Math.ceil(remaining / (double) slots);
    }

    public static int remainingDays(LocalDate asOf, YearMonth period) {
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        if (asOf.isAfter(end)) {
            return 0;
        }
        LocalDate from = asOf.isBefore(start) ? start : asOf;
        return (int) ChronoUnit.DAYS.between(from, end) + 1;
    }

    public static int remainingWeeks(LocalDate asOf, YearMonth period) {
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        if (asOf.isAfter(end)) {
            return 0;
        }
        LocalDate from = asOf.isBefore(start) ? start : asOf;
        Set<Long> weeks = new HashSet<>();
        for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
            int week = d.get(WeekFields.ISO.weekOfWeekBasedYear());
            int year = d.get(WeekFields.ISO.weekBasedYear());
            weeks.add(((long) year << 16) + week);
        }
        return weeks.size();
    }

    public static List<LocalDate[]> weeksIntersecting(YearMonth period) {
        LocalDate monthEnd = period.atEndOfMonth();
        LocalDate weekStart = period.atDay(1).with(DayOfWeek.MONDAY);
        List<LocalDate[]> weeks = new ArrayList<>();
        while (!weekStart.isAfter(monthEnd)) {
            weeks.add(new LocalDate[]{weekStart, weekStart.plusDays(6)});
            weekStart = weekStart.plusWeeks(1);
        }
        return weeks;
    }

    public static LocalDate asOfForPeriod(YearMonth period, LocalDate today) {
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        if (today.isBefore(start)) {
            return start;
        }
        if (today.isAfter(end)) {
            return end;
        }
        return today;
    }
}
