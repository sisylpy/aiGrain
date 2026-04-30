package com.nongxinle.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 部门订货提醒页的次要提示（库存偏多 / 损耗与废弃偏多等）。
 */
@Data
public class GbDepReorderAuxHint implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 例如 {@code high_stock}、{@code high_loss_waste} */
    private String type;
    private String message;
}
