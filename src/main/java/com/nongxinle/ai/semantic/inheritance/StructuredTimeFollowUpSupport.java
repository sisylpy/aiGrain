package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 通用 structured time-only follow-up：仅读 V2 结构化 timeAction / time.timeSource。
 */
public final class StructuredTimeFollowUpSupport {

    private StructuredTimeFollowUpSupport() {}

    public static boolean isStructuredTimeOnlyFollowUp(AiQuerySemanticParseResult parse) {
        if (parse == null) {
            return false;
        }
        String timeAction = normalize(parse.getTimeAction());
        if (!"NEW".equals(timeAction) && !"OVERRIDE".equals(timeAction)) {
            return false;
        }
        AiQuerySemanticParseResult.TimePart time = parse.getTime();
        if (time == null) {
            return false;
        }
        String timeSource =
                SemanticTimeContractCheck.normalizeProductionTimeSource(time.getTimeSource());
        return SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT.equals(timeSource);
    }

    private static String normalize(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return token.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
