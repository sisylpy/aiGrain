package com.nongxinle.ai.followup.rewrite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpRewriteResult {

    /** 本句是否被识别为省略/指代追问（观测）。 */
    private boolean isFollowUp;
    /** 是否成功补全为可进 v2 的完整问句。 */
    private boolean canRewrite;
    /** 补全后的自然语言问句；唯一进入 v2 的业务输入（非 intent/wire）。 */
    private String completedUserQuery;
    /** Debug：LLM 自述补全原因码（观测用，非 path/wire/Tool）。 */
    private String rewriteReason;
    /** Debug：是否从 previousTurn 继承了时间窗表述。 */
    private boolean inheritedTime;
    /** Debug：是否从 previousTurn 继承了范围表述。 */
    private boolean inheritedScope;
    /** Debug：补全问句引用的实体 type（STORE/DISH/GOODS），非业务 path。 */
    private String inheritedAnchorType;
    /** Debug：补全问句引用的实体名，非 wire/Tool。 */
    private String inheritedAnchorName;
    /** 无法唯一补全时返回 clarification，不走 v2。 */
    private boolean needClarification;
    private String clarificationQuestion;
    /** LLM 补全引用的锚点列表（观测）。 */
    private List<Map<String, String>> usedAnchors;
    private FollowUpRewriteDebug debug;

    /** 未 rewrite、行为与现状一致。 */
    public static FollowUpRewriteResult passthrough() {
        return FollowUpRewriteResult.builder()
                .isFollowUp(false)
                .canRewrite(false)
                .inheritedTime(false)
                .inheritedScope(false)
                .needClarification(false)
                .build();
    }
}
