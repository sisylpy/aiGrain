package com.nongxinle.ai.workspace;

import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;

public final class AiWorkspaceParse {

    private AiWorkspaceParse() {}

    /** 解析可选 sourceCreatedAt：ISO-8601 或 yyyy-MM-dd HH:mm:ss */
    public static Date parseOptionalSourceCreatedAt(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        try {
            return Date.from(Instant.parse(s));
        } catch (DateTimeParseException ignore) {
            // fall through
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException ignore) {
            return null;
        }
    }

    /**
     * JSON 字符串或空白解析为 Long；空白返回 null。
     *
     * @throws IllegalArgumentException 非空且无法解析为 long
     */
    public static Long parseLongLenient(String raw, String fieldLabel) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + fieldLabel + ": " + raw);
        }
    }
}
