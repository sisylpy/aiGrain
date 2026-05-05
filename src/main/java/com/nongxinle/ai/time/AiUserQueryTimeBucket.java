package com.nongxinle.ai.time;

/**
 * DeepSeek 将用户自然语言时间意图归类到此枚举；{@link #UNKNOWN} 退回默认本月 MTD。
 * <p>{@link #CALENDAR_MONTH} 需在 JSON 中带 calendar_month（1–12），calendar_year 可省略（由服务端推断）。</p>
 */
public enum AiUserQueryTimeBucket {
    MTD,
    LAST_MONTH,
    LAST_WEEK,
    THIS_WEEK,
    ROLLING_7,
    ROLLING_30,
    TODAY,
    YESTERDAY,
    LAST_YEAR,
    THIS_QUARTER,
    LAST_QUARTER,
    /** 某一公历整月（含 calendar_month / 可选 calendar_year） */
    CALENDAR_MONTH,
    /** 从开业/营运起点至今（服务端用画像创建日近似；无建档日时须反问用户） */
    SINCE_OPENING,
    UNKNOWN
}
