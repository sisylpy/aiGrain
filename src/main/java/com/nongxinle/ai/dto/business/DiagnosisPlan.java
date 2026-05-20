package com.nongxinle.ai.dto.business;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营诊断 Harness（AnswerPlan 聚合）：服务端只读子域 {@link PurchaseAnswerPlan} 等组装的计划；
 * Composer / Debug / Replay 同源。契约见 {@code docs/ai/diagnosis-answer-plan.md}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisPlan {

    public static final String TYPE_OVERALL_BUSINESS_DIAGNOSIS = "OVERALL_BUSINESS_DIAGNOSIS";

    /**
     * {@link AiResultAnchor#getSourcePlanType()}：门店优先级 / 风险排序追问产出的锚点来源（与 {@link #TYPE_OVERALL_BUSINESS_DIAGNOSIS} 区分）。
     */
    public static final String ANCHOR_SOURCE_STORE_PRIORITY_RANKING = "STORE_PRIORITY_RANKING";

    /**
     * 诊断规则包版本标识（与子域 AnswerPlan 的 {@code planType} 不同）。JSON 可选用 {@code diagnosisType}。
     */
    public static final String DIAGNOSIS_TYPE_V1_AGGREGATE = "OVERALL_BUSINESS_DIAGNOSIS_V1";

    /** JSON 字段名 {@code type} */
    @JSONField(name = "type")
    private String planType;

    @JSONField(name = "diagnosisType")
    private String diagnosisType;

    private String scopeLabel;
    private String timeLabel;

    /** NORMAL / NOTICE / WARNING / RISK（与历史字段并存；新业务优先 {@link #riskLevel}）。 */
    private String diagnosisLevel;

    /**
     * 聚合风险：HIGH / MEDIUM / LOW；无结构化结论且仅缺片段时可 NOTICE。
     */
    private String riskLevel;

    private String summary;

    /** 一段话总体判断（确定性规则产出；无证据不写断言）。 */
    private String overallJudgement;

    @Builder.Default
    private List<Map<String, Object>> focusFindings = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> evidenceRows = new ArrayList<>();

    /**
     * 多店经营对比证据（仅当 structuredIntentDetail canonical 为
     * {@link com.nongxinle.ai.conversation.AiQuerySemanticLexicon#STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS}
     * 时由 {@link com.nongxinle.ai.graph.business.DiagnosisPlanBuilder} 从 toolResults 组装）。
     * 每行 {@link Map} 建议键：storeDepartmentId、storeName、revenueAmount、purchaseAmount、stockReduceAmount、
     * dishProfitCoverage、mainReasons、dataCoverage、purchaseToRevenueRatioLine（Builder 预计算）。
     * 列表顺序由 Builder 按营业额降序稳定排序。
     */
    @Builder.Default
    private List<Map<String, Object>> storeCompareEvidence = new ArrayList<>();

    /**
     * 门店经营对比「谨慎结论」全文（Builder 从 {@link #storeCompareEvidence} 生成；Composer 只宣读）。
     */
    private String storeCompareConclusion;

    /**
     * 证据条目（结构与 evidenceRows 可并存：本列表为诊断规则直接引用的 AnswerPlan 字段摘录）。
     */
    @Builder.Default
    private List<Map<String, Object>> evidenceItems = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> riskRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> actionSuggestions = new ArrayList<>();

    @Builder.Default
    private List<String> missingSections = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();

    /**
     * D-13.2：可追问门店锚点（与 {@link com.nongxinle.ai.conversation.AiConversationTurnMemory#getLastResultAnchors()} 对齐语义）。
     */
    @Builder.Default
    private List<AiResultAnchor> resultAnchors = new ArrayList<>();
}
