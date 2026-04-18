package com.nongxinle.dto;

import java.util.Collections;
import java.util.Map;

/**
 * 部门库存调整结果（供 Controller 转为 {@code R}）。
 */
public class GbDepGoodsStockAdjustResult {

    private final boolean ok;
    private final int code;
    private final String message;
    private final Map<String, Object> data;

    private GbDepGoodsStockAdjustResult(boolean ok, int code, String message, Map<String, Object> data) {
        this.ok = ok;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static GbDepGoodsStockAdjustResult error(int code, String message) {
        return new GbDepGoodsStockAdjustResult(false, code, message, Collections.emptyMap());
    }

    public static GbDepGoodsStockAdjustResult success(Map<String, Object> data) {
        return new GbDepGoodsStockAdjustResult(true, 0, null, data);
    }

    public boolean isOk() {
        return ok;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
