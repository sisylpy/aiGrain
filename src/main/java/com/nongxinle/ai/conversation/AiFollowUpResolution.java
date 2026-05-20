package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则型多轮追问解析结果（无 LLM）；供 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver} 合并上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFollowUpResolution {

    /** 是否判定为「追问」语义（继承或局部覆盖） */
    private boolean followUp;
    /** 如 SEMANTIC_STRUCTURAL_MERGE、STORE_SCOPE_FOLLOW_UP、GROUP_SCOPE_EXPAND_FOLLOW_UP */
    private String followUpType;

    private boolean inheritIntent;
    private boolean inheritTimeWindow;
    private boolean inheritOrgScope;
    private boolean inheritFocus;

    private String overrideIntentCode;
    private String overridePathCode;
    private String overrideStartDate;
    private String overrideEndDate;
    private Long overrideStoreId;
    private String overrideFocusName;

    /** 已在 {@link com.nongxinle.ai.platform.AiRunService#startRun} 写入 {@link com.nongxinle.ai.core.AiRunState#getNormalizedUserInput()} */
    private String expandedNormalizedQuestion;
    @Builder.Default
    private boolean normalizedInputExpandedAtResolvePhase = false;

    private String purchaseStructuredIntent;
    private String purchaseSourceType;

    private AiResolvedQueryIntent mergedQueryIntent;
    private AiResolvedTimeWindow mergedTimeWindow;
    private AiResolvedOrgScope mergedOrgScope;

    /** 合并后的有效路由（与 mergedQueryIntent 一致，便于日志） */
    private String effectiveIntentCode;
    private String effectivePathCode;
    /** 如 CURRENT_MESSAGE、INHERITED_PREVIOUS、FOLLOWUP_NARROW_VISIBLE_STORE */
    private String effectiveTimeWindowSource;
    private String effectiveScopeSource;
    /** {@code CURRENT_MESSAGE_EXPLICIT}：本句自带业务域；{@code INHERITED_PREVIOUS}：追问继承上轮 intent/path */
    private String effectiveIntentSource;

    /** 门店范围追问诊断：用户句中提取的店名片语 / 匹配到的门店根 departmentId */
    private String storeScopeFollowUpMentionedName;
    private Long storeScopeFollowUpMatchedStoreRootId;
}
