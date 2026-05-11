package com.nongxinle.ai.harness.replay;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer} 输出的 Map 与单轮预期对比。
 */
public final class AiHarnessExpectationComparator {

    private AiHarnessExpectationComparator() {
    }

    public static List<AiHarnessMismatch> compare(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            boolean strictStoreSqlMatch) {

        List<AiHarnessMismatch> out = new ArrayList<>();
        if (summary == null || exp == null) {
            return out;
        }

        if (StringUtils.hasText(exp.getEffectiveIntentCode())) {
            String actual = stringVal(summary.get("effectiveIntentCode"));
            if (!eq(actual, exp.getEffectiveIntentCode())) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "effectiveIntentCode", exp.getEffectiveIntentCode(), actual));
            }
        }
        if (StringUtils.hasText(exp.getEffectivePathCode())) {
            String actual = stringVal(summary.get("effectivePathCode"));
            if (!eq(actual, exp.getEffectivePathCode())) {
                out.add(mm(AiHarnessFailureType.PATH_MISMATCH, "effectivePathCode", exp.getEffectivePathCode(), actual));
            }
        }

        boolean timeSourceOk = assertTimeWindowSource(summary, exp, out);
        if (timeSourceOk) {
            assertDate(summary, exp, out);
        }

        if (StringUtils.hasText(exp.getScopeType())) {
            String actual = stringVal(summary.get("scopeType"));
            if (!eq(actual, exp.getScopeType())) {
                out.add(mm(AiHarnessFailureType.SCOPE_TYPE_MISMATCH, "scopeType", exp.getScopeType(), actual));
            }
        }

        if (strictStoreSqlMatch && exp.getVisibleStoreRootIds() != null && !exp.getVisibleStoreRootIds().isEmpty()) {
            List<Long> actual = longList(summary, "visibleStoreRootIds");
            if (!sameSortedLongs(actual, exp.getVisibleStoreRootIds())) {
                out.add(mm(
                        AiHarnessFailureType.STORE_SCOPE_MISMATCH,
                        "visibleStoreRootIds",
                        new ArrayList<>(exp.getVisibleStoreRootIds()),
                        actual));
            }
        }
        if (strictStoreSqlMatch && exp.getEffectiveSqlDepartmentIds() != null && !exp.getEffectiveSqlDepartmentIds().isEmpty()) {
            List<Long> actual = longList(summary, "expandedSqlDepartmentIds");
            if (actual == null || actual.isEmpty()) {
                actual = longList(summary, "effectiveSqlDepartmentIds");
            }
            if (!sameSortedLongs(actual, exp.getEffectiveSqlDepartmentIds())) {
                out.add(mm(
                        AiHarnessFailureType.DEPARTMENT_SCOPE_MISMATCH,
                        "expandedSqlDepartmentIds",
                        new ArrayList<>(exp.getEffectiveSqlDepartmentIds()),
                        actual));
            }
        }

        if (Boolean.TRUE.equals(exp.getCheckPurchaseSourceType())) {
            String actual = stringVal(summary.get("purchaseSourceType"));
            if (!eq(actual, blankToNull(exp.getPurchaseSourceType()))) {
                out.add(mm(AiHarnessFailureType.PURCHASE_SOURCE_MISMATCH, "purchaseSourceType", exp.getPurchaseSourceType(), actual));
            }
        }

        if (StringUtils.hasText(exp.getMentionedStore())) {
            String actual = stringVal(summary.get("mentionedStore"));
            if (!eq(trim(actual), trim(exp.getMentionedStore()))) {
                out.add(mm(AiHarnessFailureType.STORE_SCOPE_MISMATCH, "mentionedStore", exp.getMentionedStore(), actual));
            }
        }

        if (StringUtils.hasText(exp.getStructuredIntentDetail())) {
            String actualSid = structuredIntentWireFromSummary(summary);
            if (!eq(actualSid, exp.getStructuredIntentDetail())) {
                out.add(mm(
                        AiHarnessFailureType.INTENT_MISMATCH,
                        "structuredIntentDetail",
                        exp.getStructuredIntentDetail(),
                        actualSid));
            }
        }
        if (exp.getStructuredIntentDetailAnyOf() != null && !exp.getStructuredIntentDetailAnyOf().isEmpty()) {
            String actualSid = structuredIntentWireFromSummary(summary);
            if (!exp.getStructuredIntentDetailAnyOf().contains(actualSid)) {
                out.add(mm(
                        AiHarnessFailureType.INTENT_MISMATCH,
                        "structuredIntentDetail",
                        exp.getStructuredIntentDetailAnyOf(),
                        actualSid));
            }
        }

        if (StringUtils.hasText(exp.getEffectiveIntentSource())) {
            String actual = stringVal(summary.get("effectiveIntentSource"));
            if (!eq(actual, exp.getEffectiveIntentSource())) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "effectiveIntentSource", exp.getEffectiveIntentSource(), actual));
            }
        }
        if (exp.getEffectiveIntentSourceAnyOf() != null && !exp.getEffectiveIntentSourceAnyOf().isEmpty()) {
            String actual = stringVal(summary.get("effectiveIntentSource"));
            if (!exp.getEffectiveIntentSourceAnyOf().contains(actual)) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "effectiveIntentSource", exp.getEffectiveIntentSourceAnyOf(), actual));
            }
        }
        if (StringUtils.hasText(exp.getEffectiveScopeSource())) {
            String actual = stringVal(summary.get("effectiveScopeSource"));
            if (!eq(actual, exp.getEffectiveScopeSource())) {
                out.add(mm(AiHarnessFailureType.SCOPE_TYPE_MISMATCH, "effectiveScopeSource", exp.getEffectiveScopeSource(), actual));
            }
        }
        if (exp.getEffectiveScopeSourceAnyOf() != null && !exp.getEffectiveScopeSourceAnyOf().isEmpty()) {
            String actual = stringVal(summary.get("effectiveScopeSource"));
            if (!exp.getEffectiveScopeSourceAnyOf().contains(actual)) {
                out.add(mm(AiHarnessFailureType.SCOPE_TYPE_MISMATCH, "effectiveScopeSource", exp.getEffectiveScopeSourceAnyOf(), actual));
            }
        }

        return out;
    }

    /** @return 时间来源是否可作为「锚」继续比 start/end；若 AnyOf/Source 不匹配则跳过日期断言（时间与来源已报告失败）。 */
    private static boolean assertTimeWindowSource(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (exp.getEffectiveTimeWindowSourceAnyOf() != null && !exp.getEffectiveTimeWindowSourceAnyOf().isEmpty()) {
            String actual = stringVal(summary.get("effectiveTimeWindowSource"));
            if (!exp.getEffectiveTimeWindowSourceAnyOf().contains(actual)) {
                out.add(mm(AiHarnessFailureType.TIME_SOURCE_MISMATCH, "effectiveTimeWindowSource", exp.getEffectiveTimeWindowSourceAnyOf(), actual));
                return false;
            }
            return true;
        }
        if (StringUtils.hasText(exp.getEffectiveTimeWindowSource())) {
            String actual = stringVal(summary.get("effectiveTimeWindowSource"));
            if (!eq(actual, exp.getEffectiveTimeWindowSource())) {
                out.add(mm(AiHarnessFailureType.TIME_SOURCE_MISMATCH, "effectiveTimeWindowSource", exp.getEffectiveTimeWindowSource(), actual));
                return false;
            }
        }
        return true;
    }

    private static void assertDate(Map<String, Object> summary, AiHarnessReplayExpectedRound exp, List<AiHarnessMismatch> out) {
        if (StringUtils.hasText(exp.getStartDate())) {
            String actual = stringVal(summary.get("startDate"));
            if (!eq(actual, exp.getStartDate())) {
                out.add(mm(AiHarnessFailureType.TIME_WINDOW_MISMATCH, "startDate", exp.getStartDate(), actual));
            }
        }
        if (StringUtils.hasText(exp.getEndDate())) {
            String actual = stringVal(summary.get("endDate"));
            if (!eq(actual, exp.getEndDate())) {
                out.add(mm(AiHarnessFailureType.TIME_WINDOW_MISMATCH, "endDate", exp.getEndDate(), actual));
            }
        }
    }

    private static AiHarnessMismatch mm(AiHarnessFailureType t, String field, Object expected, Object actual) {
        return AiHarnessMismatch.builder().type(t).field(field).expected(expected).actual(actual).build();
    }

    /**
     * 摘要中 {@code structuredIntentDetail} 为人类可读枚举；Harness 预期仍为 wire（如 supplier_amount_ranking）。
     */
    private static String structuredIntentWireFromSummary(Map<String, Object> summary) {
        if (summary == null) {
            return null;
        }
        Object wire = summary.get("structuredIntentDetailWire");
        if (wire != null && StringUtils.hasText(wire.toString())) {
            return wire.toString().trim();
        }
        return stringVal(summary.get("structuredIntentDetail"));
    }

    private static String stringVal(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static boolean eq(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    private static boolean sameSortedLongs(List<Long> a, List<Long> b) {
        List<Long> ca = sortedCopy(a);
        List<Long> cb = sortedCopy(b);
        return ca.equals(cb);
    }

    private static List<Long> sortedCopy(List<Long> in) {
        List<Long> c = new ArrayList<>(in == null ? List.of() : in);
        c.sort(Long::compareTo);
        return c;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> longList(Map<String, Object> summary, String key) {
        Object raw = summary.get(key);
        if (raw instanceof List<?> list) {
            List<Long> out = new ArrayList<>();
            for (Object x : list) {
                if (x instanceof Number n) {
                    out.add(n.longValue());
                } else if (x instanceof String sx && StringUtils.hasText(sx)) {
                    try {
                        out.add(Long.parseLong(sx.trim()));
                    } catch (NumberFormatException ignore) {
                        // skip
                    }
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
