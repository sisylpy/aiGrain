package com.nongxinle.ai.harness.replay;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单轮 Harness 预期；字段为 null / 空列表表示「本项不断言」。
 */
@Data
public class AiHarnessReplayExpectedRound {

    private String effectiveIntentCode;
    private String effectivePathCode;
    /** 非空时 actual 必须落在其中任一 */
    private List<String> effectiveTimeWindowSourceAnyOf = new ArrayList<>();
    /** 与 anyOf 互斥：单一精确匹配 */
    private String effectiveTimeWindowSource;
    private String startDate;
    private String endDate;
    private String scopeType;

    private List<Long> visibleStoreRootIds = new ArrayList<>();
    private List<Long> effectiveSqlDepartmentIds = new ArrayList<>();

    /** true 时校验 purchaseSourceType 与下面字段（可为 null 表示要求空） */
    private Boolean checkPurchaseSourceType;
    private String purchaseSourceType;

    private String mentionedStore;

    /**
     * 非空则要求摘要中 wire 字段 {@code structuredIntentDetailWire}（或兼容旧摘要的 {@code structuredIntentDetail}
     * 若为 wire）与该值完全一致，例如 {@code supplier_amount_ranking}。（展示用枚举见摘要 {@code structuredIntentDetail}。）
     */
    private String structuredIntentDetail;

    /** 非空列表时 actual 必须为其中任一 */
    private List<String> structuredIntentDetailAnyOf = new ArrayList<>();

    private String effectiveIntentSource;
    private String effectiveScopeSource;

    /** 与时间窗类似：任一满足即 Pass */
    private List<String> effectiveIntentSourceAnyOf = new ArrayList<>();
    private List<String> effectiveScopeSourceAnyOf = new ArrayList<>();
}
