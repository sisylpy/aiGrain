package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营概览结构化输出（与成本诊断分列）；自然语言由 Answer Composer 汇总。
 *
 * @see docs/TODO_MULTI_AGENT.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBusinessOverviewResult {

    @Builder.Default
    private String agentName = "BusinessOverviewAgent";

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

    /**
     * 与 {@code business_overview_query} 中日营收看板 stats 对齐的扁平中文指标副本（前端卡片）。
     */
    private Map<String, Object> dashboardStatsCn;

    /**
     * 看板 {@code dashboard.bindings} 副本（英文字段键，值为已格式化数值）。
     */
    private Map<String, Object> dashboardBindings;

    /**
     * 前端与对话抬头：本轮统计的「范围级别 + 门店/节点覆盖」（不含内部部门 ID）。<br>
     * 建议键：{@code scopeType}、{@code scopeName}、{@code aggregationModeHint}、
     * {@code departmentCount}、{@code visibleDepartmentNodeCount}、{@code dataAvailableDepartmentCount}、{@code dataMissingDepartmentCount}、
     * {@code primaryBanner}、{@code coverageDetail}。
     */
    private Map<String, Object> overviewScope;

    /** 本期「画像 / 台账」等在子树口径下不完整的数据缺口门店列表（≠ 经营差）。集团广角 v1 填充。 */
    @Builder.Default
    private List<AiOverviewStoreIssueItem> dataMissingStores = new ArrayList<>();

    /** 有营收台账前提下，启发式识别的经营关注点（采购/单量异常等）。集团广角 v1 填充。 */
    @Builder.Default
    private List<AiOverviewStoreIssueItem> attentionStores = new ArrayList<>();

    /** 权限范围内识别到的门店（仅名称级）；集团广角由门店快照写入。 */
    @Builder.Default
    private List<AiOverviewVisibleStoreItem> visibleStores = new ArrayList<>();

    /** 本期查询覆盖到的门店及店内汇总（调试验 scope；与缺失/异常清单分列）。 */
    @Builder.Default
    private List<AiOverviewCoveredStoreItem> coveredStores = new ArrayList<>();

    /** 正文：「本次参与统计的门店」完整枚举（调试期不截断）。 */
    private String coveredStoresBrief;

    /** 正文用：至多 Top3 家门店的一句话摘要（与 Tool/SSE 下发的 priorityStoresBrief 一致）。 */
    private String priorityStoresBrief;

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
