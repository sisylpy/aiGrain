package com.nongxinle.ai.scope;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 一轮 AI 查询解析后的组织范围（部门节点集合 + 采购用 disId）。
 */
@Value
@Builder
public class AiQueryScope {

    AiConversationScopeMode mode;
    /**
     * 单店模式：门店父部门 ID；集团模式通常为 null。
     */
    Long departmentFatherId;
    /**
     * 集团会话写入的批发商 ID；单店模式可从部门带出，便于日志与采购 wide 查询。
     */
    Long distributerId;
    /**
     * {@code gb_distributer_purchase_goods.gb_DPG_distributer_id} 查询用，与业务 dis 对齐。
     */
    int disIdForPurchaseQueries;
    /**
     * 本统计窗口内应纳入的部门主键（含子部门展开结果）。
     */
    List<Integer> resolvedDepartmentIds;

    public List<Long> resolvedDepartmentIdsAsLong() {
        return resolvedDepartmentIds.stream().map(Integer::longValue).collect(Collectors.toList());
    }

    /**
     * 餐厅画像、日营收、记忆落库等仍绑定「单一部门」时的锚点；集团模式下取下辖第一个部门（若有）。
     */
    public Long profileAnchorDepartmentId() {
        if (departmentFatherId != null) {
            return departmentFatherId;
        }
        if (!resolvedDepartmentIds.isEmpty()) {
            return resolvedDepartmentIds.get(0).longValue();
        }
        return null;
    }

    public String toMarkdownFactHeader() {
        if (mode == AiConversationScopeMode.STORE) {
            return "- **AI 统计范围（单店）**：门店父部门 ID=" + departmentFatherId
                    + "，展开部门节点数=" + resolvedDepartmentIds.size()
                    + "，采购 disId=" + disIdForPurchaseQueries + "。\n";
        }
        return "- **AI 统计范围（集团）**：批发商/集团 disId=" + distributerId
                + "，下辖部门节点数=" + resolvedDepartmentIds.size()
                + "，采购 disId=" + disIdForPurchaseQueries + "。\n";
    }
}
