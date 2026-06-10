package com.nongxinle.ai.semantic.intake.grounding;

/** 实体落地族：仅登记系统已明确的对等合同或澄清策略。 */
public enum EntityGroundingFamily {
    /** dish.ingredient_cover_days ↔ warehouse.goods_supported_dish_cover，可自动切换。 */
    COVER_DAYS,
    /** dish_sales.single_dish / store_single_dish：存在性拦截，无 GOODS 自动 peer。 */
    NAMED_SALES,
    /** purchase.goods_business_analysis.v1：GOODS-only，实体不匹配时澄清。 */
    PURCHASE_GOODS_BIZ
}
