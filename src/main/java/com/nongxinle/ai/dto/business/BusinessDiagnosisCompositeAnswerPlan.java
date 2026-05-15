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
 * C-37：经营诊断 Composite 结构化 AnswerPlan（确定性骨架；不生成自然语言终稿）。
 * C-40：{@link #summaryText} 为 <strong>确定性中文摘要</strong>（Harness / 后续 Composer 输入），**非** LLM 终稿；仅由本 Plan
 * 已有字段拼接，不编造。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisCompositeAnswerPlan {

    public static final String TYPE_BUSINESS_DIAGNOSIS_COMPOSITE = "BUSINESS_DIAGNOSIS_COMPOSITE";

    @JSONField(name = "type")
    private String type;

    private String scopeLabel;
    private String timeLabel;

    private BusinessDiagnosisCompositeRevenueSummary revenueSummary;
    private BusinessDiagnosisCompositePurchaseSummary purchaseSummary;
    private BusinessDiagnosisCompositeStockReduceSummary stockReduceSummary;
    private BusinessDiagnosisCompositeDishProfitSummary dishProfitSummary;

    private BusinessDiagnosisSignals diagnosisSignals;
    private BusinessDiagnosisCompositeRiskLevel riskLevel;

    /**
     * C-40：确定性中文摘要（短段落；来源仅限本对象已填充字段；不调 LLM）。
     */
    private String summaryText;

    @Builder.Default
    private List<String> keyFindings = new ArrayList<>();
    @Builder.Default
    private List<String> suggestedNextQuestions = new ArrayList<>();

    @Builder.Default
    private List<BusinessDiagnosisDomainCoverage> dataCoverage = new ArrayList<>();

    @Builder.Default
    private List<String> degradedSteps = new ArrayList<>();

    private BusinessDiagnosisCompositeAnswerPlanDebug debug;

    @Builder.Default
    private Map<String, Object> extra = new LinkedHashMap<>();
}
