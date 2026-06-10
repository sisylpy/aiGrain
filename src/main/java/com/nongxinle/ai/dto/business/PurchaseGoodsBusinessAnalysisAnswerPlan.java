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

/** GOODS 锚点原料采购经营分析（{@code purchase.goods_business_analysis.v1}）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseGoodsBusinessAnalysisAnswerPlan {

    public static final String TYPE = "PURCHASE_GOODS_BUSINESS_ANALYSIS";
    public static final String CONTRACT_ID = "purchase.goods_business_analysis.v1";
    public static final String CARD_TYPE = "PURCHASE_GOODS_BUSINESS_ANALYSIS_CARD";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";

    @JSONField(name = "type")
    private String planType;

    private String contractId;
    private String status;

    private Integer disGoodsId;
    private String goodsName;
    private String scopeLabel;

    /** 采购统计周期（入库完成日）。 */
    private String purchaseTimeLabel;

    /** 库存快照锚定日。 */
    private String inventorySnapshotLabel;

    /** 销量基线区间（默认近 7 天）。 */
    private String salesBaselineLabel;

    /** SELF | SUPPLIER | MIXED | NONE */
    private String dominantPurchaseSource;

    @Builder.Default
    private Map<String, Object> purchaseSourceSection = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> purchaseVolumeSection = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> priceSection = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> inventorySection = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Object> salesMatchSection = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> dishRows = new ArrayList<>();

    @Builder.Default
    private List<PurchaseGoodsBusinessJudgmentSignal> judgmentSignals = new ArrayList<>();

    @Builder.Default
    private List<String> knownGaps = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
