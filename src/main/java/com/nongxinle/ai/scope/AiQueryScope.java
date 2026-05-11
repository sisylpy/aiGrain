package com.nongxinle.ai.scope;

import com.nongxinle.utils.GbConstants;
import lombok.Builder;
import lombok.Value;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 一轮 AI 查询解析后的组织范围（部门节点集合 + 采购用 disId）。
 */
@Value
@Builder(toBuilder = true)
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
    /**
     * 当前范围内 {@code gb_department_type} → 部门节点数（用于 prompt 事实抬头）。
     */
    @Builder.Default
    Map<Integer, Integer> departmentTypeCounts = Map.of();
    /**
     * 范围内 is_group_dep=1 的父级门店数量（用于 prompt 事实抬头，区分于 type 计数）。
     */
    @Builder.Default
    int parentStoreCount = 0;
    /**
     * 记忆、画像、餐厅主档查询锚点：登录用户 {@code gb_DU_department_id}；集团会话下应与 {@link #resolvedDepartmentIds} 解耦。
     */
    Long userMemoryAnchorDepartmentId;
    /**
     * 集团模式下，用户可见部门与解析后的全集团部门集合一致时，日营业额按 {@code gb_ai_daily_revenue_distributer_id}
     * 聚合，避免部门 IN 与子树展开；若已被权限收窄为非全集团子集则为 false。
     */
    @Builder.Default
    boolean groupRevenueUseDistributerWideQuery = false;

    public List<Long> resolvedDepartmentIdsAsLong() {
        return resolvedDepartmentIds.stream().map(Integer::longValue).collect(Collectors.toList());
    }

    /**
     * 餐厅画像、日营收锚点等：单店模式为父部门 ID；集团模式为下辖任一节点的历史默认（不稳定，优先用 {@link #memoryAnchorDepartmentId()}）。
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

    /**
     * 记忆落库、用户画像抽取、主档门店绑定：优先用户主部门，否则回退 {@link #profileAnchorDepartmentId()}。
     */
    public Long memoryAnchorDepartmentId() {
        if (userMemoryAnchorDepartmentId != null) {
            return userMemoryAnchorDepartmentId;
        }
        return profileAnchorDepartmentId();
    }

    public String toMarkdownFactHeader() {
        String base;
        if (mode == AiConversationScopeMode.STORE) {
            int subCount = resolvedDepartmentIds.size() - 1;
            base = "- **AI 统计范围（单店/Subtree）**：门店 ID=" + departmentFatherId
                    + "，展开节点数=" + resolvedDepartmentIds.size()
                    + "（含门店自身），**子部门数(不含门店)=" + subCount + "**"
                    + "，采购 disId=" + disIdForPurchaseQueries + "。\n";
        } else {
            base = "- **AI 统计范围（集团）**：批发商/集团 disId=" + distributerId
                    + "，下辖部门节点数=" + resolvedDepartmentIds.size()
                    + "，**父级门店数(is_group_dep=1)=" + parentStoreCount + "**"
                    + "，采购 disId=" + disIdForPurchaseQueries + "。\n"
                    + "- **⚠️ 重要约束**：当用户询问\"几家店/几个店/多少家门店/门店数量\"时，**必须**以上面的【父级门店数】为准，不得使用下面的类型节点计数。\n";
        }
        if (departmentTypeCounts == null || departmentTypeCounts.isEmpty()) {
            return base;
        }
        List<Map.Entry<Integer, Integer>> entries = departmentTypeCounts.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .collect(Collectors.toList());
        String dist = entries.stream()
                .map(e -> departmentTypeChineseLabel(e.getKey()) + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return base + "  - 展开节点按部门类型计数：" + dist + "。\n";
    }

    private static String departmentTypeChineseLabel(int type) {
        Integer t = type;
        if (GbConstants.DepartmentType.GROUP_OFFICE.equals(t)) {
            return "总部";
        }
        if (GbConstants.DepartmentType.STORE.equals(t)) {
            return "type=1节点(门店+子部门混计，非门店数)";
        }
        if (GbConstants.DepartmentType.GROUP_PURCHASE.equals(t)) {
            return "集采";
        }
        if (GbConstants.DepartmentType.WAREHOUSE.equals(t)) {
            return "库房";
        }
        if (GbConstants.DepartmentType.CENTRAL_KITCHEN.equals(t)) {
            return "中央厨房";
        }
        if (GbConstants.DepartmentType.DELIVERY_SUPPLIER.equals(t)) {
            return "配送商";
        }
        if (GbConstants.DepartmentType.FRANCHISE.equals(t)) {
            return "加盟";
        }
        if (GbConstants.DepartmentType.REGION.equals(t)) {
            return "片区";
        }
        return "类型" + type;
    }

    /**
     * 将类型统计合并入构建器（不可变拷贝）。
     */
    public static Map<Integer, Integer> toTypeCountMap(List<com.nongxinle.dto.DepartmentTypeCountRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> m = new LinkedHashMap<>();
        for (com.nongxinle.dto.DepartmentTypeCountRow r : rows) {
            if (r.getDeptType() == null || r.getCnt() == null) {
                continue;
            }
            m.merge(r.getDeptType(), r.getCnt(), Integer::sum);
        }
        return Map.copyOf(m);
    }
}
