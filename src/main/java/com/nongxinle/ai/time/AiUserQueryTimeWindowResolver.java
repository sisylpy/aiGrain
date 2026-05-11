package com.nongxinle.ai.time;

import cn.hutool.core.util.StrUtil;
import com.nongxinle.entity.GbAiRestaurantProfileEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从用户中文口语解析 AI 注入数据的统计起止日；无法识别时退回「本月截至目前」（与原行为一致）。
 * <p>{@link #resolveRulesOnly} 未命中且 {@link #mayContainUnparsedTimeIntent} 为 true 时，由上层调用 DeepSeek
 * 归类为 {@link AiUserQueryTimeBucket} 后经 {@link #windowFromLlmOutcome} 映射为窗口。</p>
 */
public final class AiUserQueryTimeWindowResolver {

    private static final String[] TIME_INTENT_HINTS_ZH = {
            "去年", "前年", "明年", "大前年", "后年", "大后年",
            "季度", "一季度", "二季度", "三季度", "四季度",
            "上半年", "下半年", "全年", "整年", "一整年",
            "春节", "过年", "国庆", "五一", "劳动节", "端午", "中秋", "元旦", "圣诞", "清明",
            "暑假", "寒假", "黄金周", "长假", "假期",
            "前天", "大前天", "后天", "大后天",
            "哪一天", "哪天", "何日", "何时", "什么时候", "时间段", "哪段", "那段", "期间",
            "至今", "以来", "年底", "年初", "年终", "年末", "年尾", "开年",
            "月末", "月初", "月底", "上旬", "中旬", "下旬",
            "半月", "两周", "三周", "四天", "五天", "六天", "八天", "九天", "十天", "十来天", "好几天",
            "上个月", "上月", "上个季度", "上季度", "本季度", "下季度", "季初", "季末",
            "同期", "同比", "环比",
            "开业", "开张", "开店", "营业至今", "创办", "创立",
            "营运",
    };

    private static final Pattern YEAR_NUM = Pattern.compile("\\d{4}\\s*年");
    private static final Pattern MONTH_NUM = Pattern.compile("\\d{1,2}\\s*月");
    /** 如 2026年4月、2026年04月 */
    private static final Pattern EXPLICIT_YEAR_MONTH = Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月");
    /** 如 4月份、4月（需在 explicit 之后匹配） */
    private static final Pattern MONTH_NUM_SUFFIX = Pattern.compile("(\\d{1,2})\\s*月份?");

    private AiUserQueryTimeWindowResolver() {
    }

    /**
     * Skill 选择与独立「时间口径」DeepSeek 调用共用的 bucket 说明（含今日日期与 CALENDAR_MONTH 字段规则）。
     */
    public static String sharedLlmStatTimeBucketGuide(LocalDate today) {
        LocalDate t = today != null ? today : LocalDate.now();
        return "今天是 "
                + t
                + "（公历，yyyy-MM-dd）。\n"
                + "若用户完全未提及时间、或说不清统计区间，bucket 选 MTD。\n\n"
                + "字段 calendar_year、calendar_month **仅当 bucket 为 CALENDAR_MONTH 时必填 calendar_month（1～12）**；"
                + "calendar_year 为四位公历年；若用户没说年份且你能从语境推断则填写，否则填 null（服务端会按「当月序号大于今天所在月则用去年」推断）。\n\n"
                + "bucket 必须是下列之一（英文大写）：\n"
                + "- MTD：本月1号至今天；或未提及时间、说不清具体窗口。\n"
                + "- LAST_MONTH：上个月 / 上月完整自然月。\n"
                + "- LAST_WEEK：上周完整自然周（周一至周日）。\n"
                + "- THIS_WEEK：本周（周一至今天或至本周日）。\n"
                + "- ROLLING_7：最近一周、七天滚动（含今天）。\n"
                + "- ROLLING_30：最近一个月、三十天滚动（含今天）。\n"
                + "- TODAY：仅今天。\n"
                + "- YESTERDAY：仅昨天。\n"
                + "- LAST_YEAR：去年、上一年完整自然年。\n"
                + "- THIS_QUARTER：本季度、当季第一天至今天或当季末日。\n"
                + "- LAST_QUARTER：上一完整季度。\n"
                + "- CALENDAR_MONTH：用户指向某一个公历月（如「四月」「4月份」「去年六月」）。必须给出 calendar_month；calendar_year 规则见上。\n"
                + "- SINCE_OPENING：用户表达「从开业/开店/营运或营业第一天起到现在、营业至今、从我营业第一天到今天」等累计跨度（系统用门店画像建档创建日近似起点，不是真实开业日时用户可在后续说明）。\n"
                + "- UNKNOWN：确信需要时间窗口但以上都不合适（尽量少用）。\n"
                + "\n【与数据库查询区间对齐的显式日期】（强烈建议每次填写）\n"
                + "除 bucket 外请在同一 JSON 对象（skill 的 statTime 或本接口根对象）中填写：\n"
                + "- stat_start_date：统计闭区间起点。填 yyyy-MM-dd；若起点为「开业/建档首日」且你不知道具体日历日，填 **OPENING_ANCHOR**（服务端用门店画像创建日）。\n"
                + "- stat_end_date：统计闭区间终点。填 yyyy-MM-dd；若截止到「今天」，填 **TODAY**。\n"
                + "二者须与 bucket 语义一致；服务端**优先**用这两个字段换算查库起止日，并与固定规则交叉校验。\n";
    }

    /**
     * @param startInclusive 含首末日
     * @param endInclusive   含首末日
     * @param resolutionNote 注入日志与可选事实说明用
     */
    public record Window(LocalDate startInclusive, LocalDate endInclusive, String resolutionNote) {
    }

    /**
     * 规则 + 默认 MTD（不调模型）。
     */
    public static Window resolve(String userMessage, LocalDate today) {
        LocalDate t = today != null ? today : LocalDate.now();
        String msg = StrUtil.nullToEmpty(userMessage).trim();
        if (msg.isEmpty()) {
            return defaultEmptyUserMessageWindow(t);
        }
        return resolveRulesOnly(msg, t).orElseGet(() -> defaultMonthToDate(t));
    }

    public static Window defaultEmptyUserMessageWindow(LocalDate today) {
        LocalDate t = today != null ? today : LocalDate.now();
        return monthToDate(t, "默认：本月截至目前（未识别时间用语）");
    }

    public static Window defaultMonthToDate(LocalDate today) {
        LocalDate t = today != null ? today : LocalDate.now();
        return monthToDate(t, "默认：本月截至目前");
    }

    /**
     * 仅规则命中时返回窗口；否则 empty（上层可结合 DeepSeek 归类）。
     */
    public static Optional<Window> resolveRulesOnly(String userMessage, LocalDate today) {
        LocalDate t = today != null ? today : LocalDate.now();
        String msg = StrUtil.nullToEmpty(userMessage).trim();
        if (msg.isEmpty()) {
            return Optional.empty();
        }

        if (lastCalendarMonthCue(msg)) {
            LocalDate end = t.withDayOfMonth(1).minusDays(1);
            LocalDate start = end.withDayOfMonth(1);
            return Optional.of(new Window(start, end, "解析：上个月（完整自然月）"));
        }

        Optional<Window> numericMonth = tryNumericCalendarMonth(msg, t);
        if (numericMonth.isPresent()) {
            return numericMonth;
        }

        if (msg.contains("上个礼拜") || msg.contains("上礼拜") || msg.contains("上个星期")
                || msg.contains("上周") || msg.contains("上星期")) {
            LocalDate thisMon = t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate prevMon = thisMon.minusWeeks(1);
            LocalDate prevSun = prevMon.plusDays(6);
            return Optional.of(new Window(prevMon, prevSun, "解析：上周（周一至周日）"));
        }

        if (msg.contains("这个礼拜") || msg.contains("本礼拜") || msg.contains("这个星期")
                || msg.contains("本星期") || msg.contains("本周") || msg.contains("这周")) {
            LocalDate weekMon = t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekSun = weekMon.plusDays(6);
            LocalDate end = t.isBefore(weekSun) ? t : weekSun;
            return Optional.of(new Window(weekMon, end, "解析：本周（周一至当日或本周日止）"));
        }

        Integer quarter = quarterOfYearCue(msg);
        if (quarter != null) {
            int year = t.getYear();
            if (msg.contains("去年")) {
                year -= 1;
            }
            LocalDate start = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
            LocalDate quarterEnd = start.plusMonths(3).minusDays(1);
            LocalDate end = t.getYear() == year && t.isBefore(quarterEnd) ? t : quarterEnd;
            String note = msg.contains("去年")
                    ? "解析：去年第" + quarter + "季度（完整自然季）"
                    : "解析：当年第" + quarter + "季度（季首至今日或当季末日）";
            return Optional.of(new Window(start, end, note));
        }

        if (msg.contains("近30天") || msg.contains("近三十天") || msg.contains("过去30天") || msg.contains("过去三十天")
                || msg.contains("最近30天") || msg.contains("最近三十天")) {
            return Optional.of(rollingDays(t, 30, "解析：最近30天（含今天共30天）"));
        }

        if (msg.contains("近7天") || msg.contains("近七天") || msg.contains("过去7天") || msg.contains("过去七天")
                || msg.contains("最近7天") || msg.contains("最近七天")) {
            return Optional.of(rollingDays(t, 7, "解析：最近7天"));
        }

        if (msg.contains("本月") || msg.contains("这个月") || msg.contains("当月")) {
            return Optional.of(monthToDate(t, "解析：本月截至目前"));
        }

        if (bareRecentCue(msg)) {
            return Optional.of(rollingDays(t, 7, "解析：最近（默认滚动7天，含今天）"));
        }

        // 「从开业第一天到今天」同时含「开业」与「今天」：勿误判为「统计区间仅今天」
        if (openingToNowCue(msg)) {
            return Optional.empty();
        }

        if (msg.contains("今天") || msg.contains("今日")) {
            return Optional.of(new Window(t, t, "解析：今天"));
        }
        if (msg.contains("昨天") || msg.contains("昨日")) {
            LocalDate y = t.minusDays(1);
            return Optional.of(new Window(y, y, "解析：昨天"));
        }

        return Optional.empty();
    }

    /**
     * 「从我开业到现在」等：起点取 {@link GbAiRestaurantProfileEntity#getGbAiRestaurantProfileCreateTime()} 对应本地日；
     * 画像未建档则 empty（交由 DeepSeek 或反问）。
     */
    public static Optional<Window> tryOpeningToNowWindow(String userMessage, LocalDate today,
                                                         GbAiRestaurantProfileEntity profile) {
        LocalDate t = today != null ? today : LocalDate.now();
        String msg = StrUtil.nullToEmpty(userMessage).trim();
        if (!openingToNowCue(msg)) {
            return Optional.empty();
        }
        LocalDate anchor = openingAnchorDate(profile);
        if (anchor == null) {
            return Optional.empty();
        }
        LocalDate start = anchor.isAfter(t) ? t : anchor;
        return Optional.of(new Window(start, t,
                "解析：开业/营运至今（起点取门店画像创建日 " + anchor + "；若与实际开业日不一致请说明）"));
    }

    /** 画像创建日 → 本地日历日；无则 null。 */
    public static LocalDate openingAnchorDate(GbAiRestaurantProfileEntity profile) {
        if (profile == null || profile.getGbAiRestaurantProfileCreateTime() == null) {
            return null;
        }
        return profile.getGbAiRestaurantProfileCreateTime().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private static boolean openingToNowCue(String msg) {
        if (msg.contains("营业至今") || msg.contains("开业至今") || msg.contains("开张至今") || msg.contains("开店至今")) {
            return true;
        }
        boolean openCtx = msg.contains("开业") || msg.contains("开张") || msg.contains("开店")
                || msg.contains("营业以来") || msg.contains("创办") || msg.contains("创立")
                || msg.contains("营业的第一天") || msg.contains("第一天营业")
                || (msg.contains("营业") && msg.contains("第一天"))
                || (msg.contains("营运") && msg.contains("第一天"));
        // 「到今天/到今日」与「到现在/至今」同为「跨度截止到当下」
        boolean toNow = msg.contains("到现在") || msg.contains("至今")
                || msg.contains("到今天") || msg.contains("到今日");
        if (openCtx && toNow) {
            return true;
        }
        // 「开业/开店第一天 … 今天」类口头跨度（不一定出现「至今」「到今天」连用）
        boolean firstDayOpening = msg.contains("开业第一天") || msg.contains("开张第一天") || msg.contains("开店第一天")
                || (msg.contains("第一天") && msg.contains("开业"))
                || msg.contains("营业的第一天") || msg.contains("第一天营业")
                || (msg.contains("第一天") && msg.contains("营业"))
                || (msg.contains("第一天") && msg.contains("营运"));
        return firstDayOpening && (msg.contains("今天") || msg.contains("今日") || msg.contains("至今") || msg.contains("到现在"));
    }

    /** 用户是否在问「从开业/开店起到现在」类跨度（用于无建档日时反问）。 */
    public static boolean isOpeningToNowCue(String userMessage) {
        return openingToNowCue(StrUtil.nullToEmpty(userMessage).trim());
    }

    /**
     * 规则未命中时：若用户话里出现常见时间相关字眼，可触发 DeepSeek 归类。
     */
    public static boolean mayContainUnparsedTimeIntent(String userMessage) {
        String msg = StrUtil.nullToEmpty(userMessage).trim();
        if (msg.isEmpty()) {
            return false;
        }
        // 「从我营业的第一天到今天为止」：不单含零散 hint，但必须走时间解析（勿提前默认 MTD）
        if ((msg.contains("营业") || msg.contains("营运")) && msg.contains("第一天")
                && (msg.contains("今天") || msg.contains("今日") || msg.contains("至今")
                || msg.contains("到今天") || msg.contains("到今日") || msg.contains("到现在"))) {
            return true;
        }
        if (YEAR_NUM.matcher(msg).find() || MONTH_NUM.matcher(msg).find()) {
            return true;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("q1") || lower.contains("q2") || lower.contains("q3") || lower.contains("q4")) {
            return true;
        }
        for (String hint : TIME_INTENT_HINTS_ZH) {
            if (msg.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 模型给出的 {@code stat_start_date} / {@code stat_end_date}（yyyy-MM-dd 或 OPENING_ANCHOR / TODAY）优先于 bucket 换算。
     *
     * @return 解析成功返回窗口；缺字段或非法则 null，交由 bucket 分支处理
     */
    private static Window windowFromExplicitStatDatesIfPresent(LocalDate t,
                                                                 AiUserQueryTimeWindowLlmParser.LlmTimeOutcome o,
                                                                 GbAiRestaurantProfileEntity profile,
                                                                 String suffix) {
        String rawStart = StrUtil.nullToEmpty(o.statStartDateRaw()).trim();
        String rawEnd = StrUtil.nullToEmpty(o.statEndDateRaw()).trim();
        if (StrUtil.isBlank(rawStart)) {
            return null;
        }
        LocalDate start;
        if (isOpeningAnchorToken(rawStart)) {
            LocalDate anchor = openingAnchorDate(profile);
            if (anchor == null) {
                return null;
            }
            start = anchor;
        } else {
            LocalDate parsedStart = parseFlexibleIsoDate(rawStart);
            if (parsedStart == null) {
                return null;
            }
            start = parsedStart;
        }
        LocalDate end;
        if (StrUtil.isBlank(rawEnd) || isTodayToken(rawEnd)) {
            end = t;
        } else {
            LocalDate parsedEnd = parseFlexibleIsoDate(rawEnd);
            if (parsedEnd == null) {
                return null;
            }
            end = parsedEnd;
        }
        if (start.isAfter(end)) {
            return null;
        }
        if (end.isAfter(t)) {
            end = t;
        }
        if (start.isAfter(t)) {
            return null;
        }
        return new Window(start, end,
                "DeepSeek：模型显式查询区间 stat_start_date/stat_end_date → " + start + "～" + end + suffix);
    }

    private static boolean isOpeningAnchorToken(String s) {
        if (StrUtil.isBlank(s)) {
            return false;
        }
        String u = s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return "OPENING_ANCHOR".equals(u) || "PROFILE_ANCHOR".equals(u) || "OPENING".equals(u);
    }

    private static boolean isTodayToken(String s) {
        if (StrUtil.isBlank(s)) {
            return false;
        }
        String u = s.trim().toUpperCase(Locale.ROOT);
        return "TODAY".equals(u) || "NOW".equals(u);
    }

    /**
     * 仅接受 ISO {@code yyyy-MM-dd}；模型误加时分秒时取日期部分。
     */
    private static LocalDate parseFlexibleIsoDate(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        String x = s.trim();
        int space = x.indexOf(' ');
        if (space > 0) {
            x = x.substring(0, space).trim();
        }
        int tsep = x.indexOf('T');
        if (tsep > 0) {
            x = x.substring(0, tsep).trim();
        }
        try {
            return LocalDate.parse(x);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * DeepSeek 返回的 bucket 映射为统计窗口（无画像时 {@code profile} 传 null）。
     */
    public static Window windowFromLlmOutcome(LocalDate today, AiUserQueryTimeWindowLlmParser.LlmTimeOutcome o) {
        return windowFromLlmOutcome(today, o, null);
    }

    /**
     * DeepSeek 返回的 bucket（含 {@link AiUserQueryTimeBucket#CALENDAR_MONTH}、{@link AiUserQueryTimeBucket#SINCE_OPENING}）映射为统计窗口。
     */
    public static Window windowFromLlmOutcome(LocalDate today, AiUserQueryTimeWindowLlmParser.LlmTimeOutcome o,
                                             GbAiRestaurantProfileEntity profile) {
        LocalDate t = today != null ? today : LocalDate.now();
        String suffix = StrUtil.isBlank(o.reason()) ? "" : "（模型说明：" + o.reason() + "）";
        Window explicit = windowFromExplicitStatDatesIfPresent(t, o, profile, suffix);
        if (explicit != null) {
            return explicit;
        }
        return switch (o.bucket()) {
            case CALENDAR_MONTH -> {
                Integer mo = o.calendarMonth();
                if (mo == null || mo < 1 || mo > 12) {
                    yield monthToDate(t, "DeepSeek：CALENDAR_MONTH 缺合法 calendar_month，退回本月截至目前" + suffix);
                }
                Integer y = o.calendarYear();
                int yr = y != null ? y : inferYearForCalendarMonth(mo, t);
                yield calendarMonthWindow(yr, mo, t, "DeepSeek：" + yr + "年" + mo + "月" + suffix);
            }
            case MTD -> monthToDate(t, "DeepSeek：本月截至目前" + suffix);
            case LAST_MONTH -> {
                LocalDate end = t.withDayOfMonth(1).minusDays(1);
                LocalDate start = end.withDayOfMonth(1);
                yield new Window(start, end, "DeepSeek：上个月（完整自然月）" + suffix);
            }
            case LAST_WEEK -> {
                LocalDate thisMon = t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate prevMon = thisMon.minusWeeks(1);
                LocalDate prevSun = prevMon.plusDays(6);
                yield new Window(prevMon, prevSun, "DeepSeek：上周（周一至周日）" + suffix);
            }
            case THIS_WEEK -> {
                LocalDate weekMon = t.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate weekSun = weekMon.plusDays(6);
                LocalDate end = t.isBefore(weekSun) ? t : weekSun;
                yield new Window(weekMon, end, "DeepSeek：本周（周一至当日或本周日止）" + suffix);
            }
            case ROLLING_7 -> rollingDays(t, 7, "DeepSeek：滚动7天（含今天）" + suffix);
            case ROLLING_30 -> rollingDays(t, 30, "DeepSeek：滚动30天（含今天）" + suffix);
            case TODAY -> new Window(t, t, "DeepSeek：今天" + suffix);
            case YESTERDAY -> {
                LocalDate y = t.minusDays(1);
                yield new Window(y, y, "DeepSeek：昨天" + suffix);
            }
            case LAST_YEAR -> {
                LocalDate start = LocalDate.of(t.getYear() - 1, Month.JANUARY, 1);
                LocalDate end = LocalDate.of(t.getYear() - 1, Month.DECEMBER, 31);
                yield new Window(start, end, "DeepSeek：去年（完整自然年）" + suffix);
            }
            case THIS_QUARTER -> {
                LocalDate start = firstDayOfQuarterContaining(t);
                LocalDate endQ = start.plusMonths(3).minusDays(1);
                LocalDate end = t.isBefore(endQ) ? t : endQ;
                yield new Window(start, end, "DeepSeek：本季度截至目前" + suffix);
            }
            case LAST_QUARTER -> {
                LocalDate startThisQ = firstDayOfQuarterContaining(t);
                LocalDate lastPrev = startThisQ.minusDays(1);
                LocalDate start = firstDayOfQuarterContaining(lastPrev);
                yield new Window(start, lastPrev, "DeepSeek：上一完整季度" + suffix);
            }
            case SINCE_OPENING -> {
                LocalDate anchor = openingAnchorDate(profile);
                if (anchor == null) {
                    yield monthToDate(t, "DeepSeek：SINCE_OPENING 但无画像建档日（应已由上层反问）" + suffix);
                }
                LocalDate start = anchor.isAfter(t) ? t : anchor;
                yield new Window(start, t, "DeepSeek：开业/营运至今（起点取画像创建日 " + anchor + "）" + suffix);
            }
            case UNKNOWN -> monthToDate(t, "DeepSeek：未能归入已知口径，退回本月截至目前" + suffix);
        };
    }

    /** 公历整月起止：当月若含「今天」则截至今日，否则整月。 */
    public static Window calendarMonthWindow(int year, int month, LocalDate today, String resolutionNote) {
        LocalDate t = today != null ? today : LocalDate.now();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate last = start.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate end = year == t.getYear() && month == t.getMonthValue()
                ? (t.isBefore(last) ? t : last)
                : last;
        return new Window(start, end, resolutionNote);
    }

    private static Optional<Window> tryNumericCalendarMonth(String msg, LocalDate t) {
        Matcher ey = EXPLICIT_YEAR_MONTH.matcher(msg);
        if (ey.find()) {
            try {
                int y = Integer.parseInt(ey.group(1));
                int mo = Integer.parseInt(ey.group(2));
                if (mo < 1 || mo > 12) {
                    return Optional.empty();
                }
                return Optional.of(calendarMonthWindow(y, mo, t, "解析：" + y + "年" + mo + "月（数字年+月）"));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        Matcher mn = MONTH_NUM_SUFFIX.matcher(msg);
        if (mn.find()) {
            try {
                int mo = Integer.parseInt(mn.group(1));
                if (mo < 1 || mo > 12) {
                    return Optional.empty();
                }
                int y = inferYearForCalendarMonth(mo, t);
                return Optional.of(calendarMonthWindow(y, mo, t, "解析：" + y + "年" + mo + "月（数字月份）"));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** 未写年份时：月份大于当前月则认为去年该月，否则今年该月。 */
    private static int inferYearForCalendarMonth(int month, LocalDate t) {
        if (month > t.getMonthValue()) {
            return t.getYear() - 1;
        }
        return t.getYear();
    }

    private static LocalDate firstDayOfQuarterContaining(LocalDate d) {
        int m = d.getMonthValue();
        int qStartMonth = ((m - 1) / 3) * 3 + 1;
        return LocalDate.of(d.getYear(), qStartMonth, 1);
    }

    /** 第一季度 / 一季度 / q1 … q4（「去年」在调用处处理年份）。 */
    private static Integer quarterOfYearCue(String msg) {
        String u = msg.toLowerCase(Locale.ROOT);
        if (msg.contains("第四季度") || msg.contains("四季度") || u.contains("q4")) {
            return 4;
        }
        if (msg.contains("第三季度") || msg.contains("三季度") || u.contains("q3")) {
            return 3;
        }
        if (msg.contains("第二季度") || msg.contains("二季度") || u.contains("q2")) {
            return 2;
        }
        if (msg.contains("第一季度") || msg.contains("一季度") || u.contains("q1")) {
            return 1;
        }
        return null;
    }

    /**
     * 「比上月」「较上月」等里的「上月」是环比语境，不按「整月上个月」解析。
     */
    private static boolean lastCalendarMonthCue(String msg) {
        if (msg.contains("上个月")) {
            return true;
        }
        if (!msg.contains("上月")) {
            return false;
        }
        return !msg.contains("比上月") && !msg.contains("较上月") && !msg.contains("环比上月")
                && !msg.contains("同比上月") && !msg.contains("比起上月");
    }

    /** 「最近一次」类指单次事件，不按「最近7天」收窄整段注入。 */
    private static boolean bareRecentCue(String msg) {
        return msg.contains("最近") && !msg.contains("最近一次") && !msg.contains("最近一回");
    }

    private static Window monthToDate(LocalDate today, String note) {
        return new Window(today.withDayOfMonth(1), today, note);
    }

    private static Window rollingDays(LocalDate today, int daysInclusive, String note) {
        int n = daysInclusive < 1 ? 7 : daysInclusive;
        LocalDate start = today.minusDays(n - 1L);
        return new Window(start, today, note);
    }
}
