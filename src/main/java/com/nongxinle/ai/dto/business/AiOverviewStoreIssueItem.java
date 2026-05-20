package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 集团经营概览：单店异常/缺口展示项（不包含内部部门 id）。
 *
 * @see com.nongxinle.ai.dto.business.AiDishProfitOverviewResult#getDataMissingStores()
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOverviewStoreIssueItem {
    /** 门店根部门 id（仅结构化展示，正文勿逐项播报 id）。 */
    private Long storeDepartmentId;

    /** 门店显示名 */
    private String storeName;

    /** 人类可读说明（可多原因用「；」拼接） */
    private String reason;

    /**
     * 经营异常类可用：如 {@code warning} / {@code high}；数据缺失类可为 null。
     */
    private String riskLevel;
}
