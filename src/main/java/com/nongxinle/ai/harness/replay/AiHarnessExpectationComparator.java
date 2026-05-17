package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.alibaba.fastjson2.JSON;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
            if (!sameSortedLongs(actual, exp.getEffectiveSqlDepartmentIds())) {
                out.add(mm(
                        AiHarnessFailureType.DEPARTMENT_SCOPE_MISMATCH,
                        "expandedSqlDepartmentIds",
                        new ArrayList<>(exp.getEffectiveSqlDepartmentIds()),
                        actual));
            }
        }

        if (strictStoreSqlMatch && exp.getQueryStoreIds() != null && !exp.getQueryStoreIds().isEmpty()) {
            List<Integer> actual = intList(summary, "queryStoreIds");
            if (!sameSortedInts(actual, exp.getQueryStoreIds())) {
                out.add(mm(
                        AiHarnessFailureType.STORE_SCOPE_MISMATCH,
                        "queryStoreIds",
                        new ArrayList<>(exp.getQueryStoreIds()),
                        actual));
            }
        }

        if (exp.getVisibleStoreRootCountMin() != null) {
            List<Long> actualRoots = longList(summary, "visibleStoreRootIds");
            int min = exp.getVisibleStoreRootCountMin();
            if (actualRoots.size() < min) {
                out.add(mm(
                        AiHarnessFailureType.STORE_SCOPE_MISMATCH,
                        "visibleStoreRootIds.size>=" + min,
                        min,
                        actualRoots.size()));
            }
        }

        assertRevenueAnswerPlanPlanType(summary, exp, out);

        assertQuerySemanticEffectiveMentionedStoreNames(summary, exp, out);

        assertMultiStoreHarnessFlags(summary, exp, out);

        if (Boolean.TRUE.equals(exp.getMentionedDishNameMustBeAbsent())) {
            String dn = stringVal(summary.get("mentionedDishName"));
            if (StringUtils.hasText(dn)) {
                out.add(mm(
                        AiHarnessFailureType.INTENT_MISMATCH,
                        "mentionedDishName(mustBeAbsent)",
                        "(blank)",
                        dn));
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

        assertSemanticV2HarnessFields(summary, exp, out);
        assertQuerySemanticV2TimeActionExpected(summary, exp, out);
        assertQuerySemanticV2MetricActionExpected(summary, exp, out);
        assertQuerySemanticV2TimeActionNoneOf(summary, exp, out);
        assertQuerySemanticV2TimeTypeNoneOf(summary, exp, out);
        assertForbiddenSummarySubstrings(summary, exp, out);
        assertQuerySemanticV2ForbiddenKeys(summary, exp, out);
        assertEffectiveTimeWindowSourceNoneOf(summary, exp, out);
        assertEffectiveIntentCodeNoneOf(summary, exp, out);
        assertPurchaseSourceTypeNoneOf(summary, exp, out);
        assertMentionedDishNameEquals(summary, exp, out);
        assertDishProfitMetricType(summary, exp, out);
        assertOrchestrationTaskModeExpected(summary, exp, out);
        assertQueryScopeModeKindExpected(summary, exp, out);
        assertQueryStoreIdsMustContainSubset(summary, exp, out);
        assertResolvedReplayMirrorIds(summary, exp, out);
        assertConsumedAnswerPlansMustContain(summary, exp, out);
        assertMissingAnswerPlansEmpty(summary, exp, out);
        assertAnswerPreviewContract(summary, exp, out);
        assertBusinessDiagnosisDataCompletenessRevenue(summary, exp, out);
        assertBusinessOverviewMultiAgentCommitted(summary, exp, out);
        assertBusinessOverviewSuccessfulDomainsMustContain(summary, exp, out);
        assertScopeLabelSubstrings(summary, exp, out);
        assertSummaryActionItemsForbiddenSubstrings(summary, exp, out);
        assertUsedToolsMustContain(summary, exp, out);
        assertOptionalBooleanProbe(
                summary, exp.getMasterRevenueToolResultSuccessExpected(), "masterRevenueToolResultSuccess", out);
        assertOptionalBooleanProbe(
                summary, exp.getMasterPurchaseToolResultSuccessExpected(), "masterPurchaseToolResultSuccess", out);
        assertOptionalBooleanProbe(
                summary,
                exp.getMasterStockReduceToolResultSuccessExpected(),
                "masterStockReduceToolResultSuccess",
                out);
        assertOptionalBooleanProbe(
                summary, exp.getMasterDishProfitToolResultSuccessExpected(), "masterDishProfitToolResultSuccess", out);
        assertHarnessReplayContextProbes(summary, exp, out);
        assertDiagnosisStoreCompareHarnessProbes(summary, exp, out);

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

    private static void assertRevenueAnswerPlanPlanType(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        String expected = blankToNull(exp.getRevenueAnswerPlanPlanType());
        if (!StringUtils.hasText(expected)) {
            return;
        }
        String actualMounted = stringVal(summary.get("revenueAnswerPlanType"));
        if (StringUtils.hasText(actualMounted)) {
            if (!eq(actualMounted.trim(), expected)) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "revenueAnswerPlanType", expected, actualMounted));
            }
            return;
        }
        if (DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING.equals(expected)) {
            String path = stringVal(summary.get("effectivePathCode"));
            String wire = structuredIntentWireFromSummary(summary);
            boolean okPath = AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(path);
            boolean okWire = AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(wire);
            if (okPath && okWire) {
                return;
            }
            out.add(mm(
                    AiHarnessFailureType.INTENT_MISMATCH,
                    "revenueAnswerPlanType(resolvedReplayProbe)",
                    expected,
                    "path=" + path + ",wire=" + wire));
            return;
        }
        out.add(mm(
                AiHarnessFailureType.INTENT_MISMATCH,
                "revenueAnswerPlanType",
                expected,
                "(absent summary.revenueAnswerPlanType; cannot probe non-store-ranking expectation)"));
    }

    private static void assertQuerySemanticEffectiveMentionedStoreNames(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> want = trimNonEmpty(exp.getQuerySemanticEffectiveMentionedStoreNames());
        if (want.isEmpty()) {
            return;
        }
        List<String> raw = nestedStringList(summary, "querySemanticEffectiveMentionedStoreNames");
        List<String> got = trimNonEmpty(raw);
        if (!sameSortedStrings(want, got)) {
            out.add(mm(AiHarnessFailureType.STORE_SCOPE_MISMATCH, "querySemanticEffectiveMentionedStoreNames", want, got));
        }
    }

    private static void assertMultiStoreHarnessFlags(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        assertOptionalBoolean(summary, exp.getMultiStoreScopeDetectedExpected(), "multiStoreScopeDetected", out);
        assertOptionalBoolean(summary, exp.getMultiStoreScopeAppliedExpected(), "multiStoreScopeApplied", out);
        assertOptionalBoolean(
                summary,
                exp.getSingleStoreNarrowingBlockedExpected(),
                "singleStoreNarrowingBlocked",
                out);
        List<String> wantStores = trimNonEmpty(exp.getMultiStoreMatchedStoresExpected());
        if (wantStores.isEmpty()) {
            return;
        }
        List<String> gotStores = nestedStringList(summary, "multiStoreMatchedStores");
        List<String> got = trimNonEmpty(gotStores);
        if (!sameSortedStrings(wantStores, got)) {
            out.add(mm(AiHarnessFailureType.STORE_SCOPE_MISMATCH, "multiStoreMatchedStores", wantStores, got));
        }
    }

    private static void assertOptionalBoolean(
            Map<String, Object> summary,
            Boolean expectedEqual,
            String key,
            List<AiHarnessMismatch> out) {
        if (expectedEqual == null) {
            return;
        }
        Object raw = summary.get(key);
        if (!(raw instanceof Boolean b)) {
            out.add(mm(AiHarnessFailureType.STORE_SCOPE_MISMATCH, key, expectedEqual, raw));
            return;
        }
        if (!expectedEqual.equals(b)) {
            out.add(mm(AiHarnessFailureType.STORE_SCOPE_MISMATCH, key, expectedEqual, b));
        }
    }

    private static List<String> trimNonEmpty(List<String> in) {
        List<String> names = new ArrayList<>();
        if (in == null) {
            return names;
        }
        for (String s : in) {
            String t = s == null ? "" : s.trim();
            if (StringUtils.hasText(t)) {
                names.add(t);
            }
        }
        return names;
    }

    private static boolean sameSortedStrings(List<String> a, List<String> b) {
        List<String> ca = new ArrayList<>(a);
        List<String> cb = new ArrayList<>(b == null ? List.of() : b);
        Collections.sort(ca);
        Collections.sort(cb);
        return ca.equals(cb);
    }

    private static List<String> nestedStringList(Map<String, Object> summary, String key) {
        Object raw = summary.get(key);
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Object x : list) {
            if (x == null) {
                continue;
            }
            String s = x.toString().trim();
            if (StringUtils.hasText(s)) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * {@code consumedAnswerPlans} 摘要项可能为 {@code DailyRevenueAnswerPlan:REVENUE_OVERVIEW} 等「类名:子类型」字符串。
     */
    private static boolean consumedAnswerPlansEntryMatchesPlan(List<String> consumed, String planClassSimpleName) {
        if (!StringUtils.hasText(planClassSimpleName) || consumed == null || consumed.isEmpty()) {
            return false;
        }
        String name = planClassSimpleName.trim();
        for (String s : consumed) {
            if (!StringUtils.hasText(s)) {
                continue;
            }
            if (s.equals(name) || s.startsWith(name + ":")) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameSortedInts(List<Integer> a, List<Integer> b) {
        List<Integer> ca = sortedCopyInt(a);
        List<Integer> cb = sortedCopyInt(b);
        return ca.equals(cb);
    }

    private static List<Integer> sortedCopyInt(List<Integer> in) {
        List<Integer> c = new ArrayList<>(in == null ? List.of() : in);
        c.sort(Integer::compareTo);
        return c;
    }

    private static void assertSemanticV2HarnessFields(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (StringUtils.hasText(exp.getSemanticAdoptedFromExpected())) {
            String actual = stringVal(summary.get("semanticAdoptedFrom"));
            if (!eq(actual, exp.getSemanticAdoptedFromExpected())) {
                out.add(mm(
                        AiHarnessFailureType.INTENT_MISMATCH,
                        "semanticAdoptedFrom",
                        exp.getSemanticAdoptedFromExpected(),
                        actual));
            }
        }
        if (Boolean.FALSE.equals(exp.getSemanticFallbackUsedExpected())) {
            Object raw = summary.get("semanticFallbackUsed");
            if (!(raw instanceof Boolean b) || b) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "semanticFallbackUsed", false, raw));
            }
        }
        if (Boolean.FALSE.equals(exp.getQuerySemanticV2ParseMissingExpected())) {
            Object raw = summary.get("querySemanticV2ParseMissing");
            if (!(raw instanceof Boolean b) || b) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "querySemanticV2ParseMissing", false, raw));
            }
        }
    }

    private static void assertQuerySemanticV2TimeActionExpected(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (!StringUtils.hasText(exp.getQuerySemanticV2TimeActionExpected())) {
            return;
        }
        String actual = stringVal(summary.get("querySemanticV2TimeAction"));
        if (!eq(actual, exp.getQuerySemanticV2TimeActionExpected().trim())) {
            out.add(mm(
                    AiHarnessFailureType.INTENT_MISMATCH,
                    "querySemanticV2TimeAction",
                    exp.getQuerySemanticV2TimeActionExpected(),
                    actual));
        }
    }

    private static void assertQuerySemanticV2MetricActionExpected(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (!StringUtils.hasText(exp.getQuerySemanticV2MetricActionExpected())) {
            return;
        }
        String actual = stringVal(summary.get("querySemanticV2MetricAction"));
        if (!eq(actual, exp.getQuerySemanticV2MetricActionExpected().trim())) {
            out.add(mm(
                    AiHarnessFailureType.INTENT_MISMATCH,
                    "querySemanticV2MetricAction",
                    exp.getQuerySemanticV2MetricActionExpected(),
                    actual));
        }
    }

    private static void assertQuerySemanticV2TimeActionNoneOf(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> banned = exp.getQuerySemanticV2TimeActionNoneOf();
        if (banned == null || banned.isEmpty()) {
            return;
        }
        String actual = stringVal(summary.get("querySemanticV2TimeAction"));
        if (!StringUtils.hasText(actual)) {
            return;
        }
        String norm = normalizeHarnessActionToken(actual);
        for (String b : banned) {
            if (!StringUtils.hasText(b)) {
                continue;
            }
            if (norm.equals(normalizeHarnessActionToken(b))) {
                out.add(mm(
                        AiHarnessFailureType.INTENT_MISMATCH,
                        "querySemanticV2TimeAction(noneOf)",
                        banned,
                        actual));
                return;
            }
        }
    }

    private static void assertQuerySemanticV2TimeTypeNoneOf(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> banned = exp.getQuerySemanticV2TimeTypeNoneOf();
        if (banned == null || banned.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> v2 = (Map<String, Object>) summary.get("querySemanticV2");
        if (v2 == null) {
            return;
        }
        Object timeNode = v2.get("time");
        if (!(timeNode instanceof Map<?, ?> timeMap)) {
            return;
        }
        Object ttRaw = timeMap.get("timeType");
        String actual = ttRaw == null ? null : ttRaw.toString().trim();
        if (!StringUtils.hasText(actual)) {
            return;
        }
        String norm = normalizeSemanticTimeTypeToken(actual);
        for (String b : banned) {
            if (!StringUtils.hasText(b)) {
                continue;
            }
            if (norm.equals(normalizeSemanticTimeTypeToken(b))) {
                out.add(mm(
                        AiHarnessFailureType.TIME_SOURCE_MISMATCH,
                        "querySemanticV2.time.timeType(noneOf)",
                        banned,
                        actual));
                return;
            }
        }
    }

    private static String normalizeHarnessActionToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String normalizeSemanticTimeTypeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static void assertForbiddenSummarySubstrings(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> subs = exp.getForbiddenSubstringsInSummaryJson();
        if (subs == null || subs.isEmpty()) {
            return;
        }
        String json = JSON.toJSONString(summary);
        for (String s : subs) {
            if (StringUtils.hasText(s) && json.contains(s)) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "forbiddenSubstringInSummary", s, "(present)"));
            }
        }
    }

    private static void assertQuerySemanticV2ForbiddenKeys(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (!Boolean.TRUE.equals(exp.getEnforceQuerySemanticV2ScopeKeyAbsence())) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> v2 = (Map<String, Object>) summary.get("querySemanticV2");
        @SuppressWarnings("unchecked")
        Map<String, Object> prev = (Map<String, Object>) summary.get("querySemanticV2InputPreview");
        Set<String> bad = new HashSet<>();
        collectForbiddenKeysRecursive(v2, bad);
        collectForbiddenKeysRecursive(prev, bad);
        if (!bad.isEmpty()) {
            out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "querySemanticV2 forbidden keys", "(absent)", bad));
        }
    }

    private static final Set<String> V2_FORBIDDEN_SCOPE_KEYS =
            Set.of("queryStoreIds", "departmentIds", "expandedSqlDepartmentIds");

    private static void collectForbiddenKeysRecursive(Object node, Set<String> badKeys) {
        if (node instanceof Map<?, ?> m) {
            for (Object k : m.keySet()) {
                if (k != null && V2_FORBIDDEN_SCOPE_KEYS.contains(k.toString())) {
                    badKeys.add(k.toString());
                }
            }
            for (Object v : m.values()) {
                collectForbiddenKeysRecursive(v, badKeys);
            }
        } else if (node instanceof List<?> list) {
            for (Object v : list) {
                collectForbiddenKeysRecursive(v, badKeys);
            }
        }
    }

    private static void assertEffectiveTimeWindowSourceNoneOf(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> banned = exp.getEffectiveTimeWindowSourceNoneOf();
        if (banned == null || banned.isEmpty()) {
            return;
        }
        String actual = stringVal(summary.get("effectiveTimeWindowSource"));
        if (actual != null && banned.contains(actual)) {
            out.add(mm(AiHarnessFailureType.TIME_SOURCE_MISMATCH, "effectiveTimeWindowSource(noneOf)", banned, actual));
        }
    }

    private static void assertEffectiveIntentCodeNoneOf(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> banned = exp.getEffectiveIntentCodeNoneOf();
        if (banned == null || banned.isEmpty()) {
            return;
        }
        String actual = stringVal(summary.get("effectiveIntentCode"));
        if (actual != null && banned.contains(actual)) {
            out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "effectiveIntentCode(noneOf)", banned, actual));
        }
    }

    private static void assertPurchaseSourceTypeNoneOf(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> banned = exp.getPurchaseSourceTypeNoneOf();
        if (banned == null || banned.isEmpty()) {
            return;
        }
        String actual = stringVal(summary.get("purchaseSourceType"));
        if (actual != null && banned.contains(actual)) {
            out.add(mm(AiHarnessFailureType.PURCHASE_SOURCE_MISMATCH, "purchaseSourceType(noneOf)", banned, actual));
        }
    }

    private static void assertMentionedDishNameEquals(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (!StringUtils.hasText(exp.getMentionedDishName())) {
            return;
        }
        String actual = stringVal(summary.get("mentionedDishName"));
        if (!eq(actual, exp.getMentionedDishName().trim())) {
            out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "mentionedDishName", exp.getMentionedDishName(), actual));
        }
    }

    private static void assertDishProfitMetricType(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (!StringUtils.hasText(exp.getDishProfitMetricType())) {
            return;
        }
        String actual = stringVal(summary.get("dishProfitMetricType"));
        if (!eq(actual, exp.getDishProfitMetricType())) {
            out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "dishProfitMetricType", exp.getDishProfitMetricType(), actual));
        }
    }

    private static void assertOrchestrationTaskModeExpected(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (!StringUtils.hasText(exp.getOrchestrationTaskModeExpected())) {
            return;
        }
        String want = exp.getOrchestrationTaskModeExpected().trim();
        String actual = stringVal(summary.get("orchestrationTaskMode"));
        if (!eq(actual, want)) {
            out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "orchestrationTaskMode", want, actual));
        }
    }

    private static void assertQueryScopeModeKindExpected(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (StringUtils.hasText(exp.getQueryScopeModeExpected())) {
            String want = exp.getQueryScopeModeExpected().trim();
            String actual = stringVal(summary.get("queryScopeMode"));
            if (!eq(actual, want)) {
                out.add(mm(AiHarnessFailureType.SCOPE_TYPE_MISMATCH, "queryScopeMode", want, actual));
            }
        }
        if (StringUtils.hasText(exp.getQueryScopeKindExpected())) {
            String want = exp.getQueryScopeKindExpected().trim();
            String actual = stringVal(summary.get("queryScopeKind"));
            if (!eq(actual, want)) {
                out.add(mm(AiHarnessFailureType.SCOPE_TYPE_MISMATCH, "queryScopeKind", want, actual));
            }
        }
    }

    private static void assertQueryStoreIdsMustContainSubset(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<Integer> req = exp.getQueryStoreIdsMustContain();
        if (req == null || req.isEmpty()) {
            return;
        }
        List<Integer> actual = intList(summary, "queryStoreIds");
        Set<Integer> set = new HashSet<>(actual);
        for (Integer id : req) {
            if (id != null && !set.contains(id)) {
                out.add(mm(
                        AiHarnessFailureType.STORE_SCOPE_MISMATCH,
                        "queryStoreIds.mustContain(" + id + ")",
                        new ArrayList<>(req),
                        actual));
                return;
            }
        }
    }

    private static void assertResolvedReplayMirrorIds(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        assertLongIdsContained(
                longList(summary, "resolvedVisibleStoreRootIds"),
                exp.getResolvedVisibleStoreRootIdsMustContain(),
                "resolvedVisibleStoreRootIds",
                AiHarnessFailureType.STORE_SCOPE_MISMATCH,
                out);
        if (Boolean.TRUE.equals(exp.getResolvedEffectiveSqlDepartmentIdsNonEmpty())) {
            List<Long> sqlActual = longList(summary, "resolvedEffectiveSqlDepartmentIds");
            if (sqlActual.isEmpty()) {
                out.add(mm(
                        AiHarnessFailureType.DEPARTMENT_SCOPE_MISMATCH,
                        "resolvedEffectiveSqlDepartmentIds(nonEmpty)",
                        "(non-empty list)",
                        sqlActual));
            }
        }
        assertLongIdsContained(
                longList(summary, "resolvedEffectiveSqlDepartmentIds"),
                exp.getResolvedEffectiveSqlDepartmentIdsMustContain(),
                "resolvedEffectiveSqlDepartmentIds",
                AiHarnessFailureType.DEPARTMENT_SCOPE_MISMATCH,
                out);
    }

    private static void assertLongIdsContained(
            List<Long> actual,
            List<Long> requiredSubset,
            String field,
            AiHarnessFailureType failType,
            List<AiHarnessMismatch> out) {
        List<Long> req = requiredSubset == null ? List.of() : requiredSubset;
        if (req.isEmpty()) {
            return;
        }
        Set<Long> set = new HashSet<>(actual == null ? List.of() : actual);
        for (Long id : req) {
            if (id == null) {
                continue;
            }
            if (!set.contains(id)) {
                out.add(mm(failType, field + ".contains(" + id + ")", new ArrayList<>(req), actual));
                return;
            }
        }
    }

    private static boolean consumedAnswerPlanRowMatchesSimpleName(String gotRow, String wantSimple) {
        if (!StringUtils.hasText(wantSimple) || !StringUtils.hasText(gotRow)) {
            return false;
        }
        String w = wantSimple.trim();
        String g = gotRow.trim();
        if (w.equals(g)) {
            return true;
        }
        return g.startsWith(w + ":");
    }

    private static void assertConsumedAnswerPlansMustContain(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> wants = trimNonEmpty(exp.getConsumedAnswerPlansMustContain());
        if (wants.isEmpty()) {
            return;
        }
        List<String> got = nestedStringList(summary, "consumedAnswerPlans");
        for (String want : wants) {
            boolean hit = false;
            for (String g : got) {
                if (consumedAnswerPlanRowMatchesSimpleName(g, want)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                out.add(mm(
                        AiHarnessFailureType.INTENT_MISMATCH,
                        "consumedAnswerPlans.missing",
                        wants,
                        got));
                return;
            }
        }
    }

    private static void assertMissingAnswerPlansEmpty(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (!Boolean.TRUE.equals(exp.getMissingAnswerPlansMustBeEmpty())) {
            return;
        }
        Object raw = summary.get("missingAnswerPlans");
        if (raw == null) {
            return;
        }
        if (raw instanceof List<?> list && list.isEmpty()) {
            return;
        }
        out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "missingAnswerPlans", "(empty/null)", raw));
    }

    private static void assertAnswerPreviewContract(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> anyTok = trimNonEmpty(exp.getAnswerPreviewContainsAnyOf());
        List<String> noneTok = trimNonEmpty(exp.getAnswerPreviewMustNotContainAnyOf());
        if (anyTok.isEmpty() && noneTok.isEmpty()) {
            return;
        }
        Object rawPv = summary.get("answerPreview");
        String pv = rawPv == null ? null : rawPv.toString();
        String haystack = pv == null ? "" : pv.trim();
        if (!anyTok.isEmpty()) {
            boolean hit = false;
            for (String s : anyTok) {
                if (StringUtils.hasText(s) && haystack.contains(s.trim())) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "answerPreview(containsAnyOf)", anyTok, haystack.isEmpty() ? null : haystack));
            }
        }
        for (String ban : noneTok) {
            if (!StringUtils.hasText(ban)) {
                continue;
            }
            String bt = ban.trim();
            if (haystack.contains(bt)) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "answerPreview(mustNotContain)", "( Absent substring: '" + bt + "') ", haystack));
            }
        }
    }

    private static void assertBusinessDiagnosisDataCompletenessRevenue(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        if (!StringUtils.hasText(exp.getBusinessDiagnosisDataCompletenessRevenueExpected())) {
            return;
        }
        String want = exp.getBusinessDiagnosisDataCompletenessRevenueExpected().trim();
        Object planRaw = summary.get("businessDiagnosisPlan");
        if (planRaw instanceof Map<?, ?> plan) {
            Object dcRaw = plan.get("dataCompleteness");
            if (dcRaw instanceof Map<?, ?> dc) {
                Object rev = dc.get("revenue");
                String actual = rev == null ? null : rev.toString().trim();
                if (!eq(actual, want)) {
                    out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "businessDiagnosisPlan.dataCompleteness.revenue", want, actual));
                }
                return;
            }
        }
        if ("OK".equalsIgnoreCase(want)) {
            List<String> consumed = nestedStringList(summary, "consumedAnswerPlans");
            boolean presentOrExists =
                    Boolean.TRUE.equals(summary.get("diagnosisPlanPresent"))
                            || Boolean.TRUE.equals(summary.get("diagnosisPlanExists"));
            boolean typeOk = "OVERALL_BUSINESS_DIAGNOSIS".equals(stringVal(summary.get("diagnosisPlanType")));
            boolean hasRevenuePlan = consumedAnswerPlansEntryMatchesPlan(consumed, DailyRevenueAnswerPlan.class.getSimpleName());
            if (presentOrExists && typeOk && hasRevenuePlan) {
                return;
            }
            out.add(mm(
                    AiHarnessFailureType.INTENT_MISMATCH,
                    "businessDiagnosisPlan.dataCompleteness.revenue(compat)",
                    want,
                    "diagnosisPlanPresent=" + summary.get("diagnosisPlanPresent")
                            + " diagnosisPlanExists=" + summary.get("diagnosisPlanExists")
                            + " diagnosisPlanType=" + stringVal(summary.get("diagnosisPlanType"))
                            + " consumedAnswerPlans=" + consumed));
            return;
        }
        out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "businessDiagnosisPlan.dataCompleteness.revenue", want, "(no legacy plan map)"));
    }

    private static void assertBusinessOverviewMultiAgentCommitted(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        assertOptionalBooleanProbe(
                summary,
                exp.getBusinessOverviewMultiAgentBatchCompletedExpected(),
                "businessOverviewMultiAgentBatchCompleted",
                out);
        assertOptionalBooleanProbe(
                summary,
                exp.getBusinessOverviewAllExpectedDomainsAttemptedExpected(),
                "businessOverviewAllExpectedDomainsAttempted",
                out);
        assertOptionalBooleanProbe(
                summary,
                exp.getBusinessOverviewMultiAgentAnyDomainSuccessExpected(),
                "businessOverviewMultiAgentAnyDomainSuccess",
                out);
    }

    private static void assertBusinessOverviewSuccessfulDomainsMustContain(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> wants = trimNonEmpty(exp.getBusinessOverviewSuccessfulDomainsMustContain());
        if (wants.isEmpty()) {
            return;
        }
        List<String> got = nestedStringList(summary, "businessOverviewSuccessfulDomains");
        Set<String> gotNorm = new HashSet<>();
        for (String g : got) {
            if (StringUtils.hasText(g)) {
                gotNorm.add(g.trim());
            }
        }
        for (String want : wants) {
            String w = want.trim();
            if (!gotNorm.contains(w)) {
                out.add(mm(
                        AiHarnessFailureType.INTENT_MISMATCH,
                        "businessOverviewSuccessfulDomains.missing",
                        wants,
                        got));
                return;
            }
        }
    }

    private static void assertScopeLabelSubstrings(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> subs = trimNonEmpty(exp.getScopeLabelMustContainSubstrings());
        if (subs.isEmpty()) {
            return;
        }
        String label = stringVal(summary.get("scopeLabel"));
        String haystack = label != null ? label : "";
        for (String sub : subs) {
            String t = sub == null ? "" : sub.trim();
            if (!StringUtils.hasText(t)) {
                continue;
            }
            if (!haystack.contains(t)) {
                out.add(mm(AiHarnessFailureType.SCOPE_TYPE_MISMATCH, "scopeLabel.mustContain(" + t + ")", subs, haystack.isEmpty() ? null : haystack));
                return;
            }
        }
    }

    private static void assertSummaryActionItemsForbiddenSubstrings(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> banned = trimNonEmpty(exp.getSummaryActionItemsForbiddenSubstrings());
        if (banned.isEmpty()) {
            return;
        }
        Object ai = summary.get("actionItems");
        String blob = ai == null ? "" : JSON.toJSONString(ai);
        for (String s : banned) {
            String t = s == null ? "" : s.trim();
            if (StringUtils.hasText(t) && blob.contains(t)) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "actionItems forbidden substring", "(absent: '" + t + "')", "(present in actionItems blob)"));
            }
        }
    }

    private static void assertUsedToolsMustContain(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        List<String> wants = trimNonEmpty(exp.getUsedToolsMustContain());
        if (wants.isEmpty()) {
            return;
        }
        List<String> got = nestedStringList(summary, "usedTools");
        Set<String> gotSet = new HashSet<>(got);
        for (String w : wants) {
            if (!StringUtils.hasText(w)) {
                continue;
            }
            String t = w.trim();
            if (!gotSet.contains(t)) {
                out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, "usedTools.mustContain", wants, got));
                return;
            }
        }
    }

    private static void assertHarnessReplayContextProbes(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        assertOptionalString(summary, exp.getHarnessReplayPlanSource(), "harnessReplayPlanSource", out);
        assertOptionalString(
                summary, exp.getHarnessReplayDishProfitAnswerPlanType(), "harnessReplayDishProfitAnswerPlanType", out);
        assertOptionalString(
                summary,
                exp.getHarnessReplayDishProfitAnswerPlanSortDirection(),
                "harnessReplayDishProfitAnswerPlanSortDirection",
                out);
        assertOptionalBooleanProbe(
                summary, exp.getHarnessReplayPurchaseAnswerPlanProbePresent(), "harnessReplayPurchaseAnswerPlanProbePresent", out);
        assertOptionalString(
                summary, exp.getHarnessReplayPurchaseAnswerPlanType(), "harnessReplayPurchaseAnswerPlanType", out);
        assertOptionalBooleanProbe(
                summary, exp.getHarnessReplayRevenueAnswerPlanProbePresent(), "harnessReplayRevenueAnswerPlanProbePresent", out);
        assertOptionalString(
                summary, exp.getHarnessReplayRevenueAnswerPlanType(), "harnessReplayRevenueAnswerPlanType", out);
        assertOptionalString(
                summary, exp.getHarnessReplayStockReduceAnswerPlanType(), "harnessReplayStockReduceAnswerPlanType", out);
        assertOptionalString(
                summary,
                exp.getHarnessReplayStockReduceAnswerPlanSortDirection(),
                "harnessReplayStockReduceAnswerPlanSortDirection",
                out);
        assertOptionalString(
                summary, exp.getHarnessReplayStockReduceReduceType(), "harnessReplayStockReduceReduceType", out);
    }

    private static void assertOptionalString(
            Map<String, Object> summary,
            String expected,
            String key,
            List<AiHarnessMismatch> out) {
        if (!StringUtils.hasText(expected)) {
            return;
        }
        String actual = stringVal(summary.get(key));
        if (!eq(actual, expected)) {
            out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, key, expected, actual));
        }
    }

    private static void assertOptionalBooleanProbe(
            Map<String, Object> summary,
            Boolean expectedEqual,
            String key,
            List<AiHarnessMismatch> out) {
        if (expectedEqual == null) {
            return;
        }
        Object raw = summary.get(key);
        if (!(raw instanceof Boolean b)) {
            out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, key, expectedEqual, raw));
            return;
        }
        if (!expectedEqual.equals(b)) {
            out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, key, expectedEqual, b));
        }
    }

    /**
     * {@code business_store_status_compare_diagnosis} 等：Graph 完成后摊平的诊断与门店对比证据探针。
     */
    private static void assertDiagnosisStoreCompareHarnessProbes(
            Map<String, Object> summary,
            AiHarnessReplayExpectedRound exp,
            List<AiHarnessMismatch> out) {
        assertOptionalBooleanProbe(summary, exp.getDiagnosisPlanExistsExpected(), "diagnosisPlanExists", out);
        assertBusinessDiagnosisPlanExistsCompatible(summary, exp.getBusinessDiagnosisPlanExistsExpected(), out);
        assertOptionalIntegerEq(
                summary,
                exp.getHarnessReplayStoreCompareEvidenceRowsLenExpected(),
                "harnessReplayStoreCompareEvidenceRowsLen",
                out);
        assertOptionalString(
                summary, exp.getBusinessStoreCompareTop1StoreNameExpected(), "businessStoreCompareTop1StoreName", out);
        assertOptionalString(
                summary, exp.getBusinessStoreCompareTop2StoreNameExpected(), "businessStoreCompareTop2StoreName", out);
        assertOptionalBooleanProbe(summary, exp.getFinalAnswerTextBlankExpected(), "finalAnswerTextBlank", out);
    }

    private static void assertBusinessDiagnosisPlanExistsCompatible(
            Map<String, Object> summary, Boolean expectedEqual, List<AiHarnessMismatch> out) {
        if (expectedEqual == null) {
            return;
        }
        boolean legacy = Boolean.TRUE.equals(summary.get("businessDiagnosisPlanExists"));
        boolean present = Boolean.TRUE.equals(summary.get("diagnosisPlanPresent"));
        boolean exists = Boolean.TRUE.equals(summary.get("diagnosisPlanExists"));
        boolean actual = legacy || present || exists;
        if (!expectedEqual.equals(actual)) {
            out.add(mm(
                    AiHarnessFailureType.INTENT_MISMATCH,
                    "businessDiagnosisPlanExists(compatible)",
                    expectedEqual,
                    "businessDiagnosisPlanExists=" + summary.get("businessDiagnosisPlanExists")
                            + " diagnosisPlanPresent=" + summary.get("diagnosisPlanPresent")
                            + " diagnosisPlanExists=" + summary.get("diagnosisPlanExists")));
        }
    }

    private static void assertOptionalIntegerEq(
            Map<String, Object> summary, Integer expected, String key, List<AiHarnessMismatch> out) {
        if (expected == null) {
            return;
        }
        Object raw = summary.get(key);
        Integer actual = null;
        if (raw instanceof Number n) {
            actual = n.intValue();
        } else if (raw instanceof String s && StringUtils.hasText(s)) {
            try {
                actual = Integer.parseInt(s.trim());
            } catch (NumberFormatException ignore) {
                // leave null
            }
        }
        if (!Objects.equals(expected, actual)) {
            out.add(mm(AiHarnessFailureType.INTENT_MISMATCH, key, expected, actual));
        }
    }

    private static List<Integer> intList(Map<String, Object> summary, String key) {
        Object raw = summary.get(key);
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Integer> out = new ArrayList<>();
        for (Object x : list) {
            if (x instanceof Number n) {
                out.add(n.intValue());
            } else if (x instanceof String sx && StringUtils.hasText(sx)) {
                try {
                    out.add(Integer.parseInt(sx.trim()));
                } catch (NumberFormatException ignore) {
                    // skip
                }
            }
        }
        return out;
    }
}
