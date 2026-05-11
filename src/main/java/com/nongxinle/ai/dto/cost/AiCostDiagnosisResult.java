package com.nongxinle.ai.dto.cost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CostDiagnosisAgent 第一版结构化输出；自然语言仅在 AnswerComposerNode 生成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCostDiagnosisResult {

    @Builder.Default
    private String agentName = "CostDiagnosisAgent";

    private String summary;

    /** e.g. ok | warning | high | data_incomplete */
    private String riskLevel;

    @Builder.Default
    private List<Map<String, Object>> keyMetrics = new ArrayList<>();

    @Builder.Default
    private List<String> findings = new ArrayList<>();

    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    private Boolean needMoreData;

    @Builder.Default
    private List<String> questions = new ArrayList<>();

    public static Map<String, Object> metric(String name, Object value, String unit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        if (unit != null) {
            m.put("unit", unit);
        }
        return m;
    }
}
