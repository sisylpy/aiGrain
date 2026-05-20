package com.nongxinle.ai.harness.replay;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段 2 Harness：单轮 {@code plannedToolArgsByToolId[toolId]} 最小断言（Tool Request 层，不验 SQL 行集）。
 */
@Data
public class AiHarnessReplayExpectedPlannedToolArgs {

    private String toolId;
    private String startDate;
    private String endDate;
    private String scopeType;

    /** actual {@code expandedSqlDepartmentIds} 须包含列表中全部 id（不要求相等）。 */
    private List<Long> expandedSqlDepartmentIdsMustContain = new ArrayList<>();

    /** actual {@code purchaseSqlDepartmentIds} 须包含列表中全部 id（不要求相等）。 */
    private List<Long> purchaseSqlDepartmentIdsMustContain = new ArrayList<>();

    /** 与快照 {@code canonicalStructuredIntentDetailWire} 对齐（Lexicon canonical）。 */
    private String canonicalStructuredIntentDetailWire;

    /** 与 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#ARG_PURCHASE_NARRATIVE_MODE} 对齐。 */
    private String argsPurchaseNarrativeMode;

    /** 与 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#ARG_PURCHASE_SOURCE_FOCUS} 对齐。 */
    private String argsPurchaseSourceFocus;

    /**
     * 非空时 actual {@code args.purchaseSourceFocus} 须为其中任一（与 {@link #argsPurchaseSourceFocus} 互斥优先 anyOf）。
     */
    private List<String> argsPurchaseSourceFocusAnyOf = new ArrayList<>();
}
