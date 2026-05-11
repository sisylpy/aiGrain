package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置 Replay 断言：与 {@code docs/AI_HARNESS_REPLAY_CASES.md} Case 1 对齐。
 */
public final class AiHarnessBuiltinCases {

    /** 文档 Case 1：采购金额多轮追问（集团 admin=0）。 */
    public static final String PURCHASE_MULTITURN_1 = "PURCHASE_MULTITURN_1";

    /**
     * 语义锚点：`frozenClockDate` 对应的 LocalDate；
     * 「本月至今」为该月 1 日～锚点；「上个月」为其上一自然月闭合区间。
     */
    public record LocalDateAnchor(LocalDate frozenClock) {

        public static LocalDateAnchor frozenClock(LocalDate today) {
            return new LocalDateAnchor(today);
        }

        public String monthStartInclusive() {
            return frozenClock.withDayOfMonth(1).toString();
        }

        public String monthToDateInclusive() {
            return frozenClock.toString();
        }

        public String previousMonthFirstDay() {
            YearMonth ym = YearMonth.from(frozenClock).minusMonths(1);
            return ym.atDay(1).toString();
        }

        public String previousMonthLastDay() {
            YearMonth ym = YearMonth.from(frozenClock).minusMonths(1);
            return ym.atEndOfMonth().toString();
        }
    }

    /**
     * Case 1 预期链。
     * <ul>
     * <li>{@code visibleStoreRootIds}：集团 [1,3]、AAA→[1]、汀兰→[3] — 占位；环境与文档不一致时请将 {@link AiHarnessReplayRequest#strictStoreSqlMatch} = false。</li>
     * <li>第 6 轮校验收货渠道 {@code SUPPLIER_PURCHASE}；第 7 轮供货商排行 {@code structuredIntentDetail=supplier_amount_ranking}。</li>
     * </ul>
     */
    public static List<AiHarnessReplayExpectedRound> expectationsPurchaseMultiturn1(LocalDateAnchor anchor) {
        LocalDateAnchor a = anchor;
        List<AiHarnessReplayExpectedRound> list = new ArrayList<>();

        AiHarnessReplayExpectedRound r1 = new AiHarnessReplayExpectedRound();
        r1.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r1.setEffectivePathCode("purchase_overview_path");
        r1.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r1.getEffectiveTimeWindowSourceAnyOf().add("DEFAULT_MONTH_TO_DATE");
        r1.setStartDate(a.monthStartInclusive());
        r1.setEndDate(a.monthToDateInclusive());
        r1.setScopeType("GROUP");
        r1.getVisibleStoreRootIds().add(1L);
        r1.getVisibleStoreRootIds().add(3L);
        r1.setCheckPurchaseSourceType(Boolean.TRUE);
        r1.setPurchaseSourceType(null);
        list.add(r1);

        String p0 = a.previousMonthFirstDay();
        String p1 = a.previousMonthLastDay();

        AiHarnessReplayExpectedRound r2 = new AiHarnessReplayExpectedRound();
        r2.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r2.setEffectivePathCode("purchase_overview_path");
        r2.getEffectiveTimeWindowSourceAnyOf().add("CURRENT_MESSAGE_EXPLICIT");
        r2.getEffectiveTimeWindowSourceAnyOf().add("TIME_SHIFT");
        r2.setStartDate(p0);
        r2.setEndDate(p1);
        r2.setScopeType("GROUP");
        r2.getVisibleStoreRootIds().add(1L);
        r2.getVisibleStoreRootIds().add(3L);
        r2.setCheckPurchaseSourceType(Boolean.TRUE);
        r2.setPurchaseSourceType(null);
        list.add(r2);

        AiHarnessReplayExpectedRound r3 = new AiHarnessReplayExpectedRound();
        r3.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r3.setEffectivePathCode("purchase_overview_path");
        r3.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r3.setStartDate(p0);
        r3.setEndDate(p1);
        r3.setScopeType("STORE");
        r3.getVisibleStoreRootIds().add(1L);
        r3.setCheckPurchaseSourceType(Boolean.TRUE);
        r3.setPurchaseSourceType(null);
        r3.setMentionedStore("AAA");
        list.add(r3);

        AiHarnessReplayExpectedRound r4 = new AiHarnessReplayExpectedRound();
        r4.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r4.setEffectivePathCode("purchase_overview_path");
        r4.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r4.setStartDate(p0);
        r4.setEndDate(p1);
        r4.setScopeType("STORE");
        r4.getVisibleStoreRootIds().add(1L);
        r4.setCheckPurchaseSourceType(Boolean.TRUE);
        r4.setPurchaseSourceType("SELF_PURCHASE");
        r4.setMentionedStore("AAA");
        list.add(r4);

        AiHarnessReplayExpectedRound r5 = new AiHarnessReplayExpectedRound();
        r5.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r5.setEffectivePathCode("purchase_overview_path");
        r5.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r5.setStartDate(p0);
        r5.setEndDate(p1);
        r5.setScopeType("STORE");
        r5.getVisibleStoreRootIds().add(3L);
        r5.setCheckPurchaseSourceType(Boolean.TRUE);
        r5.setPurchaseSourceType(null);
        r5.setMentionedStore("汀兰餐厅");
        list.add(r5);

        AiHarnessReplayExpectedRound r6 = new AiHarnessReplayExpectedRound();
        r6.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r6.setEffectivePathCode("purchase_overview_path");
        r6.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r6.setStartDate(p0);
        r6.setEndDate(p1);
        r6.setScopeType("STORE");
        r6.getVisibleStoreRootIds().add(3L);
        r6.setCheckPurchaseSourceType(Boolean.TRUE);
        r6.setPurchaseSourceType("SUPPLIER_PURCHASE");
        r6.setMentionedStore("汀兰餐厅");
        list.add(r6);

        AiHarnessReplayExpectedRound r7 = new AiHarnessReplayExpectedRound();
        r7.setEffectiveIntentCode("PURCHASE_OVERVIEW");
        r7.setEffectivePathCode("purchase_overview_path");
        r7.setEffectiveTimeWindowSource("INHERITED_PREVIOUS");
        r7.setStartDate(p0);
        r7.setEndDate(p1);
        r7.setScopeType("STORE");
        r7.getVisibleStoreRootIds().add(3L);
        r7.setMentionedStore("汀兰餐厅");
        r7.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);
        r7.setCheckPurchaseSourceType(Boolean.TRUE);
        r7.setPurchaseSourceType(null);
        list.add(r7);

        return list;
    }

    private AiHarnessBuiltinCases() {
    }
}
