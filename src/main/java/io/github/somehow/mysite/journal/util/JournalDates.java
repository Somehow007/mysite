package io.github.somehow.mysite.journal.util;

import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Pattern;

public final class JournalDates {

    public static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    public static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");

    private JournalDates() {
    }

    public static void requireDate(String date) {
        if (date == null || !DATE_PATTERN.matcher(date).matches()) {
            throw new ClientException(ErrorCode.JOURNAL_DATE_INVALID);
        }
    }

    public static YearMonth requirePeriod(String period) {
        if (period == null || !PERIOD_PATTERN.matcher(period).matches()) {
            throw new ClientException(ErrorCode.JOURNAL_PERIOD_INVALID);
        }
        return YearMonth.parse(period);
    }

    public static boolean dateInPeriod(String date, String period) {
        return date != null && period != null && date.startsWith(period);
    }

    public static String nextPeriod(String period) {
        return YearMonth.parse(period).plusMonths(1).toString();
    }

    public static String mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY).toString();
    }

    public static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
