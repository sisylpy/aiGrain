package com.nongxinle.ai.dto.business;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多轮下钻：上一轮结构化结果中的「可追问锚点」（门店 / 供货商 / 菜品 / 商品等），与具体业务域解耦。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResultAnchor {

    public static final String ENTITY_TYPE_SUPPLIER = "SUPPLIER";
    public static final String ENTITY_TYPE_STORE = "STORE";
    public static final String ENTITY_TYPE_DISH = "DISH";
    public static final String ENTITY_TYPE_GOODS = "GOODS";

    /** {@link #ENTITY_TYPE_SUPPLIER} 等 */
    private String entityType;

    /** 业务口述侧 ID；可为 null */
    private String entityId;

    private String entityName;

    private Integer rank;

    /** 产出该锚点的 AnswerPlan.type，如 {@link PurchaseAnswerPlan#TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING} */
    private String sourcePlanType;

    /** 指标口径描述，如 totalPurchaseAmount */
    private String metric;

    /** 展示用金额/数值字符串 */
    private String amount;

    /** 可选扩展 JSON */
    @JSONField(name = "extraJson")
    private String extraJson;
}
