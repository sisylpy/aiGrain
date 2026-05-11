package com.nongxinle.ai.context;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiFollowUpResolution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单次 Run 的<b>唯一公共查询上下文</b>（Harness 入口）：用户身份、最终组织范围、时间窗、意图与数据口径。
 * 后续经营 / 采购 / 库存 / 菜品毛利 / 报表等 Agent 与 Tool 应<b>只读</b>本对象，禁止再从请求体或 {@code AiOrgScope}
 * 各自重复解析范围；兼容旧逻辑时须在调用处注明。
 * <p>
 * 典型只读路径：{@link #getOrgScope()}、{@link #getTimeWindow()}、{@link #getQueryIntent()}、{@link #getDataScope()}、
 * {@link #getEffectiveIntentCode()} / {@link #getEffectivePathCode()}。
 * {@link AiResolvedDataScope}：主查询维度见 {@code queryScopeKind} + {@code queryStoreIds} / {@code queryRealDepartmentIds} / {@code queryDistributerId}；
 * 业务表 {@code department_id IN} 用 {@code expandedSqlDepartmentIds}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResolvedQueryContext {

    private Long runId;
    private Long userId;

    private AiUserContext userContext;
    private AiResolvedOrgScope orgScope;
    private AiResolvedTimeWindow timeWindow;
    private AiResolvedQueryIntent queryIntent;
    private AiResolvedDataScope dataScope;

    private boolean followUp;
    private String originalQuestion;
    private String normalizedQuestion;

    private String queryScopeBanner;
    private String timeWindowLabel;
    private String answerBoundaryNote;

    /** 加载到的上一轮快照（可能为 null）。 */
    private AiConversationTurnMemory previousTurn;
    /** 本 Run 规则型追问解析结果。 */
    private AiFollowUpResolution followUpResolution;

    /** 合并追问后与 {@link #queryIntent} 一致的有效路由（便于日志与排查）。 */
    private String effectiveIntentCode;
    private String effectivePathCode;
    private String effectiveTimeWindowSource;
    private String effectiveScopeSource;
    /** 与 {@link AiFollowUpResolution#getEffectiveIntentSource()} 对齐 */
    private String effectiveIntentSource;

    /**
     * 菜品毛利：用户话术中点名的菜名（或多轮继承）；仅用于收窄 Tool/答复，非 SQL 部门列表。
     */
    private String mentionedDishName;
    /** Harness/Debug：由 structuredIntentDetail（wire）推导的指标类别，见 {@link AiQuerySemanticLexicon#dishProfitMetricTypeFromStructuredWire}。 */
    private String dishProfitMetricType;
}
