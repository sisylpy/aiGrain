package com.nongxinle.ai.dto.business;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库房库存现量 Harness：服务端生成的回答计划（Replay / Debug 同源；Composer 只读）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseAnswerPlan {

    public static final String TYPE_WAREHOUSE_STOCK_OVERVIEW = "WAREHOUSE_STOCK_OVERVIEW";
    public static final String TYPE_WAREHOUSE_STORE_AMOUNT_RANKING = "WAREHOUSE_STORE_AMOUNT_RANKING";
    public static final String TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH = "WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH";
    public static final String TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW = "WAREHOUSE_GOODS_AMOUNT_RANKING_LOW";
    public static final String TYPE_WAREHOUSE_LOW_STOCK_RISK = "WAREHOUSE_LOW_STOCK_RISK";
    public static final String TYPE_WAREHOUSE_NEAR_EXPIRY_RISK = "WAREHOUSE_NEAR_EXPIRY_RISK";
    public static final String TYPE_WAREHOUSE_INVENTORY_SUPERVISION = "WAREHOUSE_INVENTORY_SUPERVISION";

    /** 账面库存金额排行统一卡片（商品高/低、门店）。 */
    public static final String CARD_TYPE_STOCK_RANKING = "WAREHOUSE_STOCK_RANKING_CARD";

    public static final String RANKING_TYPE_GOODS_AMOUNT_LOW = "GOODS_AMOUNT_LOW";
    public static final String RANKING_TYPE_GOODS_AMOUNT_HIGH = "GOODS_AMOUNT_HIGH";
    public static final String RANKING_TYPE_STORE_AMOUNT = "STORE_AMOUNT";

    public static final String METRIC_LABEL_STOCK_AMOUNT = "账面库存金额";

    @JSONField(name = "type")
    private String planType;

    private String scopeLabel;
    private String timeLabel;

    /** {@link com.nongxinle.ai.inventory.InventoryQueryTimeKind} 名称。 */
    private String inventoryQueryTimeKind;

    /** 库存快照锚定日（ISO）。 */
    private String asOfDate;

    /** 用户可见库存口径（卡片 subtitle 首选）。 */
    private String stockSnapshotLabel;

    /** 流水/耗用基线区间文案（混合能力时有值）。 */
    private String periodFlowLabel;

    /** 内部销量/耗用基线（debug）；不对用户主文案与卡片副标题展示。 */
    private String internalBaselineLabel;

    /**
     * warehouse.near_expiry 风险子意图筛选（Intake/V2 {@code expiryRiskFilter} 搬运）。
     */
    private String expiryRiskFilter;

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> secondaryRows = new ArrayList<>();

    /** 库存监督分桶（{@code warehouse.inventory_supervision.v1}）。 */
    @Builder.Default
    private List<Map<String, Object>> sections = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
