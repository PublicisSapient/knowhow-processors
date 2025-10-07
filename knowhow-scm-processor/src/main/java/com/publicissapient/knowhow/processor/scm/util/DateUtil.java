package com.publicissapient.knowhow.processor.scm.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for date and time operations.
 * 
 * This class provides helper methods for date formatting, calculations,
 * and common date operations used throughout the application.
 */
public final class DateUtil {

    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the current date and time.
     * 
     * @return current LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Formats a LocalDateTime for display purposes.
     * 
     * @param dateTime the date time to format
     * @return formatted date string
     */
    public static String formatForDisplay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DISPLAY_DATE_FORMATTER);
    }

    /**
     * Formats a LocalDateTime as date only.
     * 
     * @param dateTime the date time to format
     * @return formatted date string (yyyy-MM-dd)
     */
    public static String formatDateOnly(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_ONLY_FORMATTER);
    }

    /**
     * Calculates the number of days between two dates.
     * 
     * @param startDate the start date
     * @param endDate the end date
     * @return number of days between the dates
     */
    public static long daysBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(startDate, endDate);
    }

    /**
     * Calculates the number of hours between two dates.
     * 
     * @param startDate the start date
     * @param endDate the end date
     * @return number of hours between the dates
     */
    public static long hoursBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return ChronoUnit.HOURS.between(startDate, endDate);
    }

    /**
     * Calculates the number of minutes between two dates.
     * 
     * @param startDate the start date
     * @param endDate the end date
     * @return number of minutes between the dates
     */
    public static long minutesBetween(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(startDate, endDate);
    }

    /**
     * Gets the start of the day for a given date.
     * 
     * @param dateTime the date time
     * @return LocalDateTime at the start of the day (00:00:00)
     */
    public static LocalDateTime startOfDay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * Gets the end of the day for a given date.
     * 
     * @param dateTime the date time
     * @return LocalDateTime at the end of the day (23:59:59.999999999)
     */
    public static LocalDateTime endOfDay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.truncatedTo(ChronoUnit.DAYS).plusDays(1).minusNanos(1);
    }

    /**
     * Gets the start of the week for a given date (Monday).
     * 
     * @param dateTime the date time
     * @return LocalDateTime at the start of the week
     */
    public static LocalDateTime startOfWeek(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.truncatedTo(ChronoUnit.DAYS)
                      .minusDays(dateTime.getDayOfWeek().getValue() - 1);
    }

    /**
     * Gets the start of the month for a given date.
     * 
     * @param dateTime the date time
     * @return LocalDateTime at the start of the month
     */
    public static LocalDateTime startOfMonth(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * Gets the end of the month for a given date.
     * 
     * @param dateTime the date time
     * @return LocalDateTime at the end of the month
     */
    public static LocalDateTime endOfMonth(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.withDayOfMonth(dateTime.toLocalDate().lengthOfMonth())
                      .truncatedTo(ChronoUnit.DAYS)
                      .plusDays(1)
                      .minusNanos(1);
    }

    /**
     * Checks if a date is within the last N days.
     * 
     * @param dateTime the date to check
     * @param days the number of days
     * @return true if the date is within the last N days, false otherwise
     */
    public static boolean isWithinLastDays(LocalDateTime dateTime, int days) {
        if (dateTime == null) {
            return false;
        }
        LocalDateTime cutoff = now().minusDays(days);
        return dateTime.isAfter(cutoff);
    }

    /**
     * Checks if a date is within the last N hours.
     * 
     * @param dateTime the date to check
     * @param hours the number of hours
     * @return true if the date is within the last N hours, false otherwise
     */
    public static boolean isWithinLastHours(LocalDateTime dateTime, int hours) {
        if (dateTime == null) {
            return false;
        }
        LocalDateTime cutoff = now().minusHours(hours);
        return dateTime.isAfter(cutoff);
    }

    /**
     * Gets a human-readable relative time description.
     * 
     * @param dateTime the date time to describe
     * @return relative time description (e.g., "2 hours ago", "3 days ago")
     */
    public static String getRelativeTimeDescription(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "Unknown";
        }

        LocalDateTime now = now();
        long minutes = minutesBetween(dateTime, now);
        long hours = hoursBetween(dateTime, now);
        long days = daysBetween(dateTime, now);

        if (minutes < 1) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else if (days < 30) {
            return days + " day" + (days == 1 ? "" : "s") + " ago";
        } else if (days < 365) {
            long months = days / 30;
            return months + " month" + (months == 1 ? "" : "s") + " ago";
        } else {
            long years = days / 365;
            return years + " year" + (years == 1 ? "" : "s") + " ago";
        }
    }

    /**
     * Creates a date range for the last N days.
     * 
     * @param days the number of days
     * @return DateRange object
     */
    public static DateRange lastNDays(int days) {
        LocalDateTime end = now();
        LocalDateTime start = end.minusDays(days);
        return new DateRange(start, end);
    }

    /**
     * Creates a date range for the last N hours.
     * 
     * @param hours the number of hours
     * @return DateRange object
     */
    public static DateRange lastNHours(int hours) {
        LocalDateTime end = now();
        LocalDateTime start = end.minusHours(hours);
        return new DateRange(start, end);
    }

    /**
     * Creates a date range for the current week.
     * 
     * @return DateRange object for the current week
     */
    public static DateRange currentWeek() {
        LocalDateTime now = now();
        LocalDateTime start = startOfWeek(now);
        LocalDateTime end = start.plusWeeks(1).minusNanos(1);
        return new DateRange(start, end);
    }

    /**
     * Creates a date range for the current month.
     * 
     * @return DateRange object for the current month
     */
    public static DateRange currentMonth() {
        LocalDateTime now = now();
        LocalDateTime start = startOfMonth(now);
        LocalDateTime end = endOfMonth(now);
        return new DateRange(start, end);
    }

    /**
     * Data class representing a date range.
     */
    public static class DateRange {
        private final LocalDateTime start;
        private final LocalDateTime end;

        public DateRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        public LocalDateTime getStart() {
            return start;
        }

        public LocalDateTime getEnd() {
            return end;
        }

        public boolean contains(LocalDateTime dateTime) {
            if (dateTime == null) {
                return false;
            }
            return !dateTime.isBefore(start) && !dateTime.isAfter(end);
        }

        public long getDurationInDays() {
            return daysBetween(start, end);
        }

        public long getDurationInHours() {
            return hoursBetween(start, end);
        }

        @Override
        public String toString() {
            return String.format("DateRange{start=%s, end=%s}", 
                formatForDisplay(start), formatForDisplay(end));
        }
    }
}