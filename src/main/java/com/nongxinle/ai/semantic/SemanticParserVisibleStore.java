package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * v2 语义解析输入：当前用户可见门店的**脱敏**简表，仅店名，不含任何数据库 ID。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticParserVisibleStore {

    /** 门店展示名（与权限内可见列表一致）。 */
    private String storeName;
}
