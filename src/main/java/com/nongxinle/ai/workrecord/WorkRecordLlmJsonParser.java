package com.nongxinle.ai.workrecord;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Slf4j
public final class WorkRecordLlmJsonParser {

    /** @deprecated transitional — remove after prompt stabilizes; only workRecord/categoryId */
    private static final String LEGACY_FIELD_WORK_RECORD = "workRecord";
    /** @deprecated transitional — remove after prompt stabilizes; only workRecord/categoryId */
    private static final String LEGACY_FIELD_CATEGORY_ID = "categoryId";

    private WorkRecordLlmJsonParser() {
    }

    public static ParseResult parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return ParseResult.failure("empty_llm_response");
        }
        String jsonText = extractJsonObjectText(raw.trim());
        if (!StringUtils.hasText(jsonText)) {
            return ParseResult.failure("json_object_not_found");
        }
        try {
            JSONObject root = JSON.parseObject(jsonText);
            if (root == null || root.isEmpty()) {
                return ParseResult.failure("empty_json_object");
            }

            boolean hasFormalPolished = root.containsKey("polishedContent");
            boolean hasLegacyWorkRecord = root.containsKey(LEGACY_FIELD_WORK_RECORD);

            String polishedContent = text(root.get("polishedContent"));
            String protocolWarning = null;

            if (!StringUtils.hasText(polishedContent) && hasLegacyWorkRecord) {
                polishedContent = text(root.get(LEGACY_FIELD_WORK_RECORD));
                if (StringUtils.hasText(polishedContent)) {
                    protocolWarning = "legacy_field:workRecord";
                    log.warn(
                            "[WorkRecordLlmJsonParser] transitional protocol: mapped workRecord -> polishedContent; remove after prompt stabilizes");
                }
            }

            if (!StringUtils.hasText(polishedContent) && !hasFormalPolished && !hasLegacyWorkRecord) {
                return ParseResult.failure("protocol_field_missing");
            }

            Long selectedCategoryId = longVal(root.get("selectedCategoryId"));
            boolean hasFormalCategoryId = root.containsKey("selectedCategoryId");
            boolean hasLegacyCategoryId = root.containsKey(LEGACY_FIELD_CATEGORY_ID);

            if (selectedCategoryId == null && hasLegacyCategoryId) {
                selectedCategoryId = longVal(root.get(LEGACY_FIELD_CATEGORY_ID));
                if (selectedCategoryId != null) {
                    protocolWarning = appendWarning(protocolWarning, "legacy_field:categoryId");
                    log.warn(
                            "[WorkRecordLlmJsonParser] transitional protocol: mapped categoryId -> selectedCategoryId; remove after prompt stabilizes");
                }
            }

            String categoryDecision = upper(text(root.get("categoryDecision")));
            if (!StringUtils.hasText(categoryDecision)
                    && selectedCategoryId != null
                    && protocolWarning != null
                    && !hasFormalCategoryId) {
                categoryDecision = WorkRecordConstants.DECISION_EXISTING;
                protocolWarning = appendWarning(protocolWarning, "inferred:categoryDecision=EXISTING");
            }

            WorkRecordLlmResult result =
                    WorkRecordLlmResult.builder()
                            .polishedContent(polishedContent)
                            .polishMode(upper(text(root.get("polishMode"))))
                            .selectedCategoryId(selectedCategoryId)
                            .selectedCategoryCode(text(root.get("selectedCategoryCode")))
                            .selectedCategoryName(text(root.get("selectedCategoryName")))
                            .categoryDecision(categoryDecision)
                            .suggestedCategoryName(text(root.get("suggestedCategoryName")))
                            .confidence(decimal(root.get("confidence")))
                            .shortReason(text(root.get("shortReason")))
                            .protocolWarning(protocolWarning)
                            .build();
            return ParseResult.success(result);
        } catch (Exception e) {
            return ParseResult.failure("json_parse_error: " + e.getMessage());
        }
    }

    private static String appendWarning(String existing, String addition) {
        if (!StringUtils.hasText(existing)) {
            return addition;
        }
        return existing + ";" + addition;
    }

    private static String extractJsonObjectText(String raw) {
        if (raw.startsWith("{") && raw.endsWith("}")) {
            return raw;
        }
        int fenceStart = raw.indexOf("```");
        if (fenceStart >= 0) {
            int bodyStart = raw.indexOf('\n', fenceStart);
            if (bodyStart >= 0) {
                int fenceEnd = raw.indexOf("```", bodyStart + 1);
                if (fenceEnd > bodyStart) {
                    String fenced = raw.substring(bodyStart + 1, fenceEnd).trim();
                    if (fenced.startsWith("json")) {
                        int nl = fenced.indexOf('\n');
                        if (nl >= 0) {
                            fenced = fenced.substring(nl + 1).trim();
                        }
                    }
                    if (fenced.startsWith("{")) {
                        return fenced;
                    }
                }
            }
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return null;
    }

    private static String text(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private static String upper(String v) {
        return v == null ? null : v.trim().toUpperCase();
    }

    private static Long longVal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record ParseResult(boolean ok, WorkRecordLlmResult value, String errorCode) {

        public static ParseResult success(WorkRecordLlmResult value) {
            return new ParseResult(true, value, null);
        }

        public static ParseResult failure(String errorCode) {
            return new ParseResult(false, null, errorCode);
        }
    }
}
