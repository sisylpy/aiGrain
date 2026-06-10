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

/** 指定商品当前仍有剩余的库存批次明细（{@code warehouse.goods_stock_batch_detail.v1}）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsStockBatchDetailAnswerPlan {

    public static final String TYPE = "GOODS_STOCK_BATCH_DETAIL";
    public static final String CONTRACT_ID = "warehouse.goods_stock_batch_detail.v1";
    public static final String CARD_TYPE = "GOODS_STOCK_BATCH_DETAIL_CARD";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";

    @JSONField(name = "type")
    private String planType;

    private String contractId;
    private String status;

    private String goodsName;
    private Integer disGoodsId;
    private String scopeLabel;
    private String stockSnapshotLabel;

    @Builder.Default
    private List<Map<String, Object>> batchRows = new ArrayList<>();

    /** 按展示单位分组的批次行（禁止跨单位合计数量）。 */
    @Builder.Default
    private List<Map<String, Object>> batchesByUnit = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<String> knownGaps = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> debug = new LinkedHashMap<>();
}
