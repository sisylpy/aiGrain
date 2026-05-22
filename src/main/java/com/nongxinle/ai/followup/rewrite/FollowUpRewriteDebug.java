package com.nongxinle.ai.followup.rewrite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpRewriteDebug {

    /** 主链恒为 {@code LLM}；规则型 {@code RULE} 链已于 Phase1-J 删除。 */
    private String detector;
    private String promptId;
    /** Historical：规则型 Rewriter 观测字段，主链不使用。 */
    private List<String> matchedPatterns;
    /** Historical：规则型 Rewriter 观测字段，主链不使用。 */
    private String anchorResolution;
    /** Historical：规则型 Rewriter 观测字段，主链不使用。 */
    private List<String> candidateAnchors;
    /** Historical：规则型 Rewriter 观测字段，主链不使用。 */
    private String templateId;
    private Double confidence;
    private String llmRawText;
    @Builder.Default
    private Map<String, Object> extras = new LinkedHashMap<>();
}
