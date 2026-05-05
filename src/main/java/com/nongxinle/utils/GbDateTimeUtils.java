package com.nongxinle.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.DateUtil;

/**
 * 日营业额 / 报表等场景共用的日期时间工具（线程安全，默认中国时区）。
 * <p>与 {@link DateUtils} 区分：本类以 {@code java.time} 为主，避免共享 {@link java.text.SimpleDateFormat} 带来的问题。</p>
 */
public final class GbDateTimeUtils {

    private GbDateTimeUtils() {
    }

    /** 业务默认时区（国内部署） */
    public static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    /** 与 {@link DateUtils#DATE_PATTERN} 一致 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    private static final Locale CHINA_LOCALE = Locale.CHINA;

    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_PATTERN).withLocale(CHINA_LOCALE);

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM").withLocale(CHINA_LOCALE);

    private static final DateTimeFormatter YEAR_ONLY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy").withLocale(CHINA_LOCALE);

    private static final String[] CHINESE_WEEKDAY_SHORT =
            {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    /** Numbers / Excel 另存后常见的斜杠日期表头，补充 {@link #DAY_FORMATTER}。 */
    private static final DateTimeFormatter[] EXCEL_DATE_STRING_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy/M/d").withLocale(CHINA_LOCALE),
            DateTimeFormatter.ofPattern("yyyy/M/dd").withLocale(CHINA_LOCALE),
            DateTimeFormatter.ofPattern("yyyy/MM/dd").withLocale(CHINA_LOCALE),
            DateTimeFormatter.ofPattern("M/d/yyyy").withLocale(Locale.US),
            DateTimeFormatter.ofPattern("MM/dd/yyyy").withLocale(Locale.US),
            DateTimeFormatter.ofPattern("d/M/yyyy").withLocale(Locale.UK),
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withLocale(Locale.UK),
    };

    public static String formatDay(LocalDate day) {
        if (day == null) {
            return null;
        }
        return day.format(DAY_FORMATTER);
    }

    public static String formatDay(Date date) {
        if (date == null) {
            return null;
        }
        return toLocalDate(date).format(DAY_FORMATTER);
    }

    /** {@code yyyy-MM}，按中国时区的日历日。 */
    public static String formatYearMonth(Date date) {
        if (date == null) {
            return null;
        }
        return toLocalDate(date).format(YEAR_MONTH_FORMATTER);
    }

    /** {@code yyyy}，按中国时区的日历日。 */
    public static String formatYear(Date date) {
        if (date == null) {
            return null;
        }
        return toLocalDate(date).format(YEAR_ONLY_FORMATTER);
    }

    public static LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(CHINA_ZONE).toLocalDate();
    }

    public static Date toDateStartOfDay(LocalDate day) {
        if (day == null) {
            return null;
        }
        return Date.from(day.atStartOfDay(CHINA_ZONE).toInstant());
    }

    public static Date startOfDay(Date date) {
        if (date == null) {
            return null;
        }
        return toDateStartOfDay(toLocalDate(date));
    }

    public static Date endOfDay(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate ld = toLocalDate(date);
        return Date.from(ld.atTime(23, 59, 59, 999_000_000).atZone(CHINA_ZONE).toInstant());
    }

    /**
     * 严格解析 {@code yyyy-MM-dd}，按中国时区对齐到当日 00:00。
     */
    public static Date parseDay(String text) {
        if (text == null) {
            throw new DateTimeParseException("null date text", "", 0);
        }
        String t = text.trim();
        if (t.isEmpty()) {
            throw new DateTimeParseException("empty date text", "", 0);
        }
        LocalDate ld = LocalDate.parse(t, DAY_FORMATTER);
        return toDateStartOfDay(ld);
    }

    public static Date parseDayLenient(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return parseDay(text);
        } catch (DateTimeParseException e) {
            return parseDayFromExcelStyleStrings(text.trim());
        }
    }

    private static Date parseDayFromExcelStyleStrings(String t) {
        for (DateTimeFormatter f : EXCEL_DATE_STRING_FORMATTERS) {
            try {
                LocalDate ld = LocalDate.parse(t, f);
                return toDateStartOfDay(ld);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    /**
     * Excel 单元格：{@link Date}、数值型 Excel 日期序列、{@link LocalDate}、常见字符串格式等；
     * 对齐到当日 00:00（中国时区）；无法识别返回 {@code null}。
     * <p>兼容 Mac Numbers 导出后再用 Excel 打开常见的表头日期数字、斜杠日期字符串等。</p>
     */
    public static Date parseExcelDateLikeCell(Object cell) {
        if (cell == null) {
            return null;
        }
        if (cell instanceof Date) {
            return toDateStartOfDay(toLocalDate((Date) cell));
        }
        if (cell instanceof LocalDate) {
            return toDateStartOfDay((LocalDate) cell);
        }
        if (cell instanceof LocalDateTime) {
            return toDateStartOfDay(((LocalDateTime) cell).toLocalDate());
        }
        if (cell instanceof Number) {
            double n = ((Number) cell).doubleValue();
            if (DateUtil.isValidExcelDate(n)) {
                Date jd = DateUtil.getJavaDate(n, false);
                return toDateStartOfDay(toLocalDate(jd));
            }
        }
        String s = cell.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        Date parsed = parseDayLenient(s);
        return parsed;
    }

    /**
     * 将单元格规范为 {@code yyyy-MM-dd} 日键（用于表头识别、按日聚合）；无法识别返回 {@code null}。
     */
    public static String normalizeExcelDayKey(Object cell) {
        Date d = parseExcelDateLikeCell(cell);
        return d == null ? null : formatDay(d);
    }

    /**
     * 与 {@code gb_ai_daily_revenue_weekday} 历史约定一致：0=周日，1=周一，…，6=周六。
     */
    public static int weekdayForAiDailyRevenue(Date date) {
        if (date == null) {
            return 1;
        }
        int v = toLocalDate(date).getDayOfWeek().getValue();
        return v == 7 ? 0 : v;
    }

    public static String chineseWeekdayShort(Date date) {
        if (date == null) {
            return CHINESE_WEEKDAY_SHORT[0];
        }
        return chineseWeekdayShort(toLocalDate(date));
    }

    public static String chineseWeekdayShort(LocalDate day) {
        int idx = day.getDayOfWeek().getValue() % 7;
        return CHINESE_WEEKDAY_SHORT[idx];
    }

    /** 含首尾两天在内的日历天数（按中国时区的日历日计算）。 */
    public static long inclusiveCalendarDaysBetween(Date startInclusive, Date endInclusive) {
        if (startInclusive == null || endInclusive == null) {
            return 0;
        }
        LocalDate s = toLocalDate(startInclusive);
        LocalDate e = toLocalDate(endInclusive);
        return ChronoUnit.DAYS.between(s, e) + 1;
    }

    public static List<LocalDate> inclusiveLocalDates(LocalDate start, LocalDate end) {
        List<LocalDate> out = new ArrayList<>();
        if (start == null || end == null || end.isBefore(start)) {
            return out;
        }
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            out.add(d);
        }
        return out;
    }

    public static LocalDate parseLocalDay(String text) {
        if (text == null) {
            throw new DateTimeParseException("null date text", "", 0);
        }
        String t = text.trim();
        if (t.isEmpty()) {
            throw new DateTimeParseException("empty date text", "", 0);
        }
        return LocalDate.parse(t, DAY_FORMATTER);
    }

    public static LocalDate todayChina() {
        return LocalDate.now(CHINA_ZONE);
    }
}
