package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 权限范围内识别到的门店（不含异常判定；与有营收汇总、coveredStores 分列）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOverviewVisibleStoreItem {

    /** 门店根部门 id；供结构化卡片与前端展示，不在对用户正文中逐项播报 id。 */
    private Long storeDepartmentId;

    private String storeName;
}
