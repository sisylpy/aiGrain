package com.nongxinle.dto;

import lombok.Data;

/**
 * {@code POST /ai/goods-add/confirm} 请求体。
 */
@Data
public class GbAiGoodsAddConfirmRequest {

    private String sessionId;
    /** NX_CATALOG | TEMP */
    private String confirmType;
    /**
     * 仅 {@code confirmType=NX_CATALOG} 时有效；缺省与 {@code USE_MATCHED} 相同。
     * <ul>
     *   <li>{@code USE_MATCHED} — 直接使用白名单内的目录 SKU（原行为）。</li>
     *   <li>{@code ADD_SIBLING_SKU} — 在该 SKU 所属品名父（nx level=2）下新增一条 SKU（nx level=3），
     *       名称与规格取请求中的 {@code goodsName}/{@code goodsSpec} 或会话快照中的用户输入。</li>
     * </ul>
     */
    private String nxCatalogIntent;
    private Integer nxGoodsId;
    private String goodsName;
    private String goodsSpec;
    /** 说明（与 analyze 的 {@code goodsFurtherDescription} 同源）；临时品确认时写入详情，缺省用会话快照。 */
    private String goodsFurtherDescription;
}
