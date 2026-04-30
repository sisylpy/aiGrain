package com.nongxinle.dto;

import lombok.Data;

/**
 * {@code POST /ai/goods-add/analyze} 请求体（与 docs/ai-add-goods-frontend-api.md 对齐）。
 */
@Data
public class GbAiGoodsAddAnalyzeRequest {

    /** 多轮时传入；首轮可空，由服务端生成 */
    private String sessionId;
    /**
     * 分析入口：
     * <ul>
     *   <li>{@code AI}（默认）— 由模型匹配一二级与 SKU；</li>
     *   <li>{@code MANUAL_CATALOG} — 逐级自选目录；</li>
     *   <li>{@code DIRECT_TEMP}（或等价 {@code ADD_TEMP}、{@code TEMP_ONLY}、{@code TEMP_GOODS}）— **不请求 DeepSeek**，
     *       跳过目录匹配，**当场添加临时商品**（返回 {@code flowState=SUCCESS}，
     *       与 **§5.2** {@code confirmType=TEMP} 成功时相同的 {@code persistedGoods}、{@code gbDistributerGoods}、{@code gbDepartmentDisGoods}）；产品侧若需二次确认，在调本接口前由前端完成即可。</li>
     * </ul>
     */
    private String analyzeMode;
    /** 手动选目录：已选一级（nx_goods_level=0）id，用于拉取二级列表 */
    private Integer manualGreatGrandNxGoodsId;
    /** 手动选目录：已选二级（nx_goods_level=1）id，用于拉取三级品名（level=2）列表 */
    private Integer manualGrandNxGoodsId;
    /** 手动选目录：已选三级品名父（nx_goods_level=2）id，用于拉取四级 SKU（level=3）列表 */
    private Integer manualFatherNxGoodsId;
    private String goodsName;
    private String goodsSpec;
    /**
     * 说明（可选）：用途、备注、助对照目录、临时品备注等可统一写在本字段；参与 DeepSeek 分类与 SKU 匹配及会话快照。
     */
    private String goodsFurtherDescription;
    private Long departmentId;
    private Integer distributerId;
    private Integer depId;
    private Integer depFatherId;

    /**
     * 用户在「未找到 SKU、需确认一二级」步骤中选中的二级目录 id（{@code nx_goods_level=1}）。
     * 与 sessionId 同传时：不再跑 L1L2/SKU 匹配，而是按会话快照中已锁定的一级 + 该二级，调用模型生成并写入农鑫目录（品名父节点 level=2 + SKU level=3），并创建批发商商品。
     */
    private Integer confirmedGrandNxGoodsId;

    /**
     * 为 {@code true} 时跳过「批发商商品 + 农鑫目录 SKU 名称预检索」（任一 {@code DIS_CATALOG_* }），直接进入 DeepSeek 一二级/SKU 流程。
     * 上一轮已展示候选但用户点了「都不是，继续 AI 对照目录」时再传同一 {@code sessionId} 与本字段即可。
     */
    private Boolean skipCatalogPrefetch;
}
