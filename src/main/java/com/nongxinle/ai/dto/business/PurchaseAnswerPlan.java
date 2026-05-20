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
 * 采购 Harness：服务端生成的回答计划（Replay / Debug / Composer 同源）。
 * <p>
 * 排序与选行在构建阶段一次性完成；后续 Composer 只消费已有字段。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAnswerPlan {

    public static final String TYPE_PURCHASE_OVERVIEW = "PURCHASE_OVERVIEW";
    public static final String TYPE_PURCHASE_SELF_OVERVIEW = "PURCHASE_SELF_OVERVIEW";
    public static final String TYPE_PURCHASE_SUPPLIER_OVERVIEW = "PURCHASE_SUPPLIER_OVERVIEW";
    public static final String TYPE_PURCHASE_GOODS_AMOUNT_RANKING = "PURCHASE_GOODS_AMOUNT_RANKING";
    public static final String TYPE_PURCHASE_GOODS_COUNT_RANKING = "PURCHASE_GOODS_COUNT_RANKING";
    public static final String TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING = "PURCHASE_SUPPLIER_AMOUNT_RANKING";

    /** 供货商渠道：按上一锚点或语义追问商品/单价明细（不重跑排行 SQL；商品行来自 Tool 已有列表）。 */
    public static final String TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL = "PURCHASE_SUPPLIER_GOODS_DETAIL";

    /** 自采渠道：承接 {@code purchase_source_goods_query} + {@code SELF_PURCHASE} 的商品列表明细（与供货商明细共用 Tool 载荷键）。 */
    public static final String TYPE_PURCHASE_SELF_GOODS_DETAIL = "PURCHASE_SELF_GOODS_DETAIL";

    /** 多店范围内：按门店采购金额对比/排序（数据来自采购 Tool 的门店覆盖行，不重跑 SQL）。 */
    public static final String TYPE_PURCHASE_STORE_AMOUNT_RANKING = "PURCHASE_STORE_AMOUNT_RANKING";

    /**
     * Phase2-A：单一 {@code disGoodsId} + 时间窗 + 权限范围下，按采购记录行 legacy 桶拆自采/供货商/其它（ALL 口径）。
     */
    public static final String TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN = "PURCHASE_GOODS_SOURCE_BREAKDOWN";

    /** JSON：{@code type}（与文档及前端对齐） */
    @JSONField(name = "type")
    private String planType;

    private String scopeLabel;
    private String timeLabel;

    /**
     * 对齐 Harness：{@link com.nongxinle.ai.conversation.AiQuerySemanticLexicon#SOURCE_SELF_PURCHASE} /
     * {@code SUPPLIER_PURCHASE} / {@code ALL}
     */
    private String purchaseSourceType;

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> secondaryRows = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();

    /** 本轮可继承的下钻锚点（由 Builder 从 Tool 已有行抽取，不重算）。 */
    @Builder.Default
    private List<AiResultAnchor> resultAnchors = new ArrayList<>();
}
